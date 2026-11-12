package net.runelite.mp.plugins

import android.content.Context
import android.util.Log
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import net.runelite.client.externalplugins.PluginHubHooks
import net.runelite.mp.client.RuntimeDexConfiguration
import net.runelite.mp.compat.JdkRetargeter
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val TAG = "PluginDexer"

/**
 * Converts upstream plugin-hub jars (JVM bytecode, dependencies bundled) to dex on the
 * device, so the hub is used exactly as desktop uses it: same manifest, same signature,
 * same jars. Runs D8 in-process the first time a jar hash is seen and caches the result
 * under filesDir — app updates keep the cache, a new plugin version gets a new hash.
 *
 * Flags mirror what the old build-side pipeline used: `--release --min-api 26`. min-api 26
 * matters beyond the floor: below it D8 desugars default interface methods onto static
 * companions, Method.isDefault() turns false, and ConfigInvocationHandler stops serving
 * @ConfigItem defaults (quest-helper NPE'd on its first enum config read).
 */
internal class PluginDexer(private val context: Context) : PluginHubHooks.Dexer
{
    private val cacheDir = File(context.filesDir, "plugin-dex").apply { mkdirs() }
    // Active compiler inputs must survive Android's low-storage cache eviction.
    private val workDir = File(context.filesDir, "plugin-dex-work")

    override fun dex(internalName: String, displayName: String?, jarHash: String, jar: File): File
    {
        // FORMAT is bumped whenever the converted jar's layout changes, so stale conversions
        // are redone (and pruned) instead of reused.
        val out = File(cacheDir, "${internalName}_${jarHash}_$FORMAT.jar")
        if (out.isFile)
        {
            out.setLastModified(System.currentTimeMillis())
            return out
        }
        ZipFile(jar).use { zip ->
            // A pre-dexed jar (a fork hub, or the old pipeline's) loads as-is.
            if (zip.getEntry("classes.dex") != null) return jar
            refuseDesktopOnly(zip)
        }

        PluginInstallTracker.converting(internalName, displayName)
        val started = System.nanoTime()
        val tmpDir = File(workDir, "${internalName}_$jarHash").apply { deleteRecursively(); mkdirs() }
        try
        {
            val compatibleJar = File(tmpDir, "compatible.jar")
            ZipFile(jar).use { input ->
                val jarClasses = input.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .map { it.name.removeSuffix(".class") }.toSet()
                ZipOutputStream(compatibleJar.outputStream().buffered()).use { output ->
                    for (entry in input.entries())
                    {
                        if (entry.isDirectory) continue
                        val bytes = input.getInputStream(entry).use { it.readBytes() }
                        output.putNextEntry(ZipEntry(entry.name))
                        try {
                            // Hub jars get the regex relocation on top of the JDK member table.
                            output.write(JdkRetargeter.rewrite(entry.name, bytes, jarClasses, true))
                        } catch (e: IllegalArgumentException) {
                            throw PluginHubHooks.DexException("JDK API compatibility: ${e.message}", e)
                        }
                        output.closeEntry()
                    }
                }
            }
            val errors = mutableListOf<String>()
            val handler = object : DiagnosticsHandler
            {
                override fun error(error: Diagnostic) { errors += error.diagnosticMessage }
                override fun warning(warning: Diagnostic) { Log.d(TAG, "$internalName: ${warning.diagnosticMessage}") }
            }
            try
            {
                D8.run(
                    RuntimeDexConfiguration.configure(context, D8Command.builder(handler), tmpDir)
                        .addProgramFiles(compatibleJar.toPath())
                        .setMinApiLevel(26)
                        .setMode(CompilationMode.RELEASE)
                        .setOutput(tmpDir.toPath(), OutputMode.DexIndexed)
                        .build()
                )
            }
            catch (e: OutOfMemoryError)
            {
                throw PluginHubHooks.DexException("ran out of memory converting it for Android", e)
            }
            catch (e: Exception)
            {
                throw PluginHubHooks.DexException(errors.firstOrNull() ?: e.message ?: e.javaClass.simpleName, e)
            }

            // Large plugins (or ones bundling big libraries) exceed 64K methods per dex and
            // come out as classes.dex, classes2.dex, … — ART loads all of them from one jar.
            val dexes = tmpDir.listFiles().orEmpty()
                .filter { it.isFile && DEX_NAME.matches(it.name) }
                .sortedBy { it.name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1 }
            if (dexes.none { it.name == "classes.dex" }) throw PluginHubHooks.DexException("D8 produced no classes.dex")

            val partial = File(cacheDir, out.name + ".part")
            ZipOutputStream(partial.outputStream().buffered()).use { zos ->
                for (dex in dexes)
                {
                    zos.putNextEntry(ZipEntry(dex.name))
                    dex.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                // Class.getResource asks the class's defining loader — this jar's
                // DexClassLoader, not PluginHubClassLoader — so the plugin's own resources
                // (icons, data files) have to sit next to its dex.
                ZipFile(jar).use { zip ->
                    for (entry in zip.entries())
                    {
                        if (entry.isDirectory || entry.name.endsWith(".class") || DEX_NAME.matches(entry.name)) continue
                        zos.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            // API 29+ refuses to load a writable dex file.
            if (!partial.setReadOnly() || !partial.renameTo(out))
            {
                partial.delete()
                throw PluginHubHooks.DexException("couldn't write the converted plugin to app storage")
            }
            pruneOtherVersions(internalName, out)
            val ms = (System.nanoTime() - started) / 1_000_000
            Log.i(TAG, "$internalName: dexed ${jar.length() / 1024} KB in $ms ms (${dexes.size} dex)")
            PluginInstallTracker.converted(internalName, ms)
            return out
        }
        finally
        {
            tmpDir.deleteRecursively()
        }
    }

    /**
     * The hub's OpenGL renderers and other LWJGL/JOGL users can't do anything on Android —
     * the classes don't exist here and the client already renders through GLES. Refuse up
     * front with a reason, instead of a NoClassDefFoundError somewhere inside startUp.
     */
    private fun refuseDesktopOnly(zip: ZipFile)
    {
        for (entry in zip.entries())
        {
            if (entry.isDirectory || !entry.name.endsWith(".class")) continue
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            val text = String(bytes, Charsets.ISO_8859_1)
            val lib = DESKTOP_ONLY.firstOrNull { text.contains(it.first) } ?: continue
            throw PluginHubHooks.DexException("needs ${lib.second}, which isn't available on Android")
        }
    }

    private fun pruneOtherVersions(internalName: String, keep: File)
    {
        cacheDir.listFiles().orEmpty()
            .filter { it != keep && it.name.startsWith("${internalName}_") }
            .forEach { it.setWritable(true); it.delete() }
    }

    companion object
    {
        /**
         * Core-library types must match the APK, including Flow publisher signatures. `jdk1` is
         * the first JdkRetargets table; bump it whenever a row or shim name changes.
         */
        private const val FORMAT = "v5-jdk1-${RuntimeDexConfiguration.CACHE_VERSION}"
        private val DEX_NAME = Regex("""classes\d*\.dex""")
        private val DESKTOP_ONLY = listOf(
            "org/lwjgl/" to "LWJGL (desktop OpenGL)",
            "com/jogamp/" to "JOGL (desktop OpenGL)",
        )

        /** Installs the dexer and the progress listener. Must run before RuneLite.main. */
        fun install(context: Context)
        {
            PluginHubHooks.dexer = PluginDexer(context.applicationContext)
            PluginHubHooks.listener = PluginInstallTracker
            PluginInstallTracker.init(context.applicationContext)
        }
    }
}
