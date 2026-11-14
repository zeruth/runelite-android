package net.runelite.mp.client

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val TAG = "ClientPreparer"

/**
 * What ClientPreparer is doing, for the splash: RuneLite's own splash (which the Compose
 * splash mirrors) only opens once RuneLite.main runs, after preparation is over.
 */
internal object ClientPrepStatus
{
    var active by androidx.compose.runtime.mutableStateOf(false)
    var action by androidx.compose.runtime.mutableStateOf("")
    var detail by androidx.compose.runtime.mutableStateOf("")
    var fraction by androidx.compose.runtime.mutableStateOf(0f)
    var text by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun set(action: String, detail: String, fraction: Float, text: String? = null)
    {
        this.action = action; this.detail = detail; this.fraction = fraction; this.text = text
    }
}

/**
 * Gets the game client onto the device the way the launcher gets it onto a desktop: the
 * `net.runelite:injected-client` artifact matching the bundled runelite-client, straight from
 * repo.runelite.net and checked against the repo's own sha256. Android can't run it as-is, so
 * it then goes through the transforms the Gradle build used to apply (ClientTransforms.kt) and
 * D8, once per artifact; later launches load the cached result, offline included.
 *
 * The result is merged into the app's own class loader, which is where upstream ClientLoader
 * looks for `client` — so the loader needs no Android changes at all.
 */
internal object ClientPreparer
{
    /** Bump whenever a transform or the packaging changes, so cached clients are rebuilt. */
    // t3: JDK member retargets moved to the JdkRetargets table (net.runelite.mp.compat.jdk).
    private const val TRANSFORM_VERSION = "t3-${RuntimeDexConfiguration.CACHE_VERSION}"
    private const val REPO = "https://repo.runelite.net/net/runelite/injected-client"

    /** Prepares (or reuses) the client for [version] and makes it loadable. Blocking. */
    fun prepare(context: Context, version: String, progress: (String) -> Unit)
    {
        val dir = File(context.filesDir, "game-client").apply { mkdirs() }
        val raw = File(dir, "injected-client-$version.jar")
        val rawSha = File(dir, raw.name + ".sha256")

        val cachedSha = rawSha.takeIf { it.isFile }?.readText()?.trim()
        val cached = cachedSha?.let { dexFor(dir, version, it) }?.takeIf { it.isFile }
        val dex = if (cached != null) cached else {
            ClientPrepStatus.active = true
            try
            {
                ClientPrepStatus.set("Preparing RuneScape", "Checking game client $version", 0f)
                val sha = fetchRaw(version, raw, rawSha, progress)
                build(context, dir, version, sha, raw, progress)
            }
            finally
            {
                ClientPrepStatus.active = false
            }
        }
        install(dex)
        progress("Game client $version ready")
    }

    private fun dexFor(dir: File, version: String, sha: String) =
        File(dir, "client-$version-${sha.take(12)}-$TRANSFORM_VERSION.jar")

    private fun fetchRaw(version: String, raw: File, rawSha: File, progress: (String) -> Unit): String
    {
        val jarUrl = "$REPO/$version/injected-client-$version.jar"
        val expected = String(get("$jarUrl.sha256"), Charsets.UTF_8).trim().substringBefore(' ').lowercase()
        require(expected.length == 64) { "Unexpected checksum from $jarUrl.sha256" }
        if (raw.isFile && sha256(raw) == expected)
        {
            rawSha.writeText(expected)
            return expected
        }
        progress("Downloading game client $version…")
        val partial = File(raw.path + ".part")
        download(jarUrl, partial) { done, total ->
            val f = if (total > 0) done.toFloat() / total else 0f
            ClientPrepStatus.set("Preparing RuneScape", "Downloading game client $version", 0.35f * f,
                if (total > 0) "%.1f / %.1f MB".format(done / 1048576f, total / 1048576f) else "%.1f MB".format(done / 1048576f))
            // Called once per whole percent; the launcher log only needs every tenth.
            val percent = if (total > 0) done * 100 / total else -1
            if (percent % 10 == 0L) progress("Downloading game client $version… $percent%")
        }
        val actual = sha256(partial)
        if (actual != expected)
        {
            partial.delete()
            throw IOException("Downloaded game client didn't match its checksum")
        }
        raw.delete()
        if (!partial.renameTo(raw)) throw IOException("Couldn't store the game client")
        rawSha.writeText(expected)
        return expected
    }

    private fun build(context: Context, dir: File, version: String, sha: String, raw: File, progress: (String) -> Unit): File
    {
        progress("Preparing game client for Android…")
        val started = System.nanoTime()
        // Android may purge cacheDir while D8 is still reading its inputs.
        val work = File(dir, "work").apply { deleteRecursively(); mkdirs() }
        try
        {
            // Same chain, same order as the old Gradle tasks.
            fun step(n: Int, what: String) =
                ClientPrepStatus.set("Preparing RuneScape", what, 0.35f + 0.02f * n, "Step ${n + 1} of 7")
            step(0, "Redirecting launcher credentials")
            val prop = File(work, "1-prop.jar")
            RewriteLauncherEnvTask().apply {
                inputJar = raw; outputJar = prop
                classEntry = "client.class"
                launcherKeys = listOf("JX_ACCESS_TOKEN", "JX_REFRESH_TOKEN", "JX_SESSION_ID", "JX_CHARACTER_ID", "JX_DISPLAY_NAME")
            }.run()
            step(1, "Locating the window mode setter")
            val windowMode = File(work, "2-windowmode.jar")
            LocateWindowModeTask().apply { inputJar = prop; outputJar = windowMode }.run()
            step(2, "Desugaring string concatenation")
            val concat = File(work, "3-concat.jar")
            DesugarStringConcatTask().apply {
                inputJar = windowMode; outputJar = concat
                helperOwner = "net/runelite/mp/util/IndyConcat"
            }.run()
            step(3, "Desugaring lambdas")
            val noLambda = File(work, "4-nolambda.jar")
            DesugarLambdasTask().apply {
                inputJar = concat; outputJar = noLambda
                syntheticPrefix = "runelite/desugar/Lambda"
            }.run()
            step(4, "Rewriting unsupported Java APIs")
            val final = File(work, "5-final.jar")
            DesugarApisTask().apply { inputJar = noLambda; outputJar = final }.run()
            Log.i(TAG, "transforms done in ${(System.nanoTime() - started) / 1_000_000} ms")

            progress("Preparing game client for Android… (converting)")
            val dexOut = File(work, "dex").apply { mkdirs() }
            val errors = mutableListOf<String>()
            // D8 has no progress callback: ease the bar toward the end of its band over about
            // as long as a conversion usually takes, with the elapsed time beside it.
            val d8Start = System.nanoTime()
            val ticker = Thread {
                while (!Thread.currentThread().isInterrupted)
                {
                    val secs = (System.nanoTime() - d8Start) / 1e9
                    val eased = 1 - Math.exp(-secs / 12.0)
                    ClientPrepStatus.set("Preparing RuneScape", "Converting the game client for Android",
                        0.45f + 0.48f * eased.toFloat(), "Step 6 of 7 · ${secs.toInt()}s")
                    try { Thread.sleep(250) } catch (e: InterruptedException) { return@Thread }
                }
            }.apply { isDaemon = true; start() }
            try
            {
            D8.run(
                D8Command.builder(object : DiagnosticsHandler
                {
                    override fun error(error: Diagnostic) { errors += error.diagnosticMessage }
                    override fun warning(warning: Diagnostic) { Log.d(TAG, warning.diagnosticMessage) }
                }).let { RuntimeDexConfiguration.configure(context, it, work) }
                    .addProgramFiles(final.toPath())
                    // As for hub plugins: below 26 D8 desugars default methods away and breaks
                    // RuneLite's config proxies; 26 is also the app's minSdk.
                    .setMinApiLevel(26)
                    .setMode(CompilationMode.RELEASE)
                    .setOutput(dexOut.toPath(), OutputMode.DexIndexed)
                    .build()
            )
            }
            finally
            {
                ticker.interrupt()
            }
            if (errors.isNotEmpty()) throw IOException("D8: ${errors.first()}")
            ClientPrepStatus.set("Preparing RuneScape", "Saving the converted client", 0.95f, "Step 7 of 7")

            val out = dexFor(dir, version, sha)
            val partial = File(out.path + ".part")
            ZipOutputStream(partial.outputStream().buffered()).use { zos ->
                dexOut.listFiles().orEmpty()
                    .filter { DEX.matches(it.name) }
                    .sortedBy { it.name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1 }
                    .forEach { dex ->
                        zos.putNextEntry(ZipEntry(dex.name))
                        dex.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                // Resources ride along: WindowModeForcer reads the properties
                // locateWindowMode wrote through the class loader that holds `client`.
                ZipFile(final).use { zip ->
                    for (entry in zip.entries())
                    {
                        if (entry.isDirectory || entry.name.endsWith(".class")) continue
                        zos.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            // API 29+ refuses writable dex files.
            if (!partial.setReadOnly() || !partial.renameTo(out)) throw IOException("Couldn't store the converted game client")
            // Older versions and transform generations are dead weight now.
            dir.listFiles().orEmpty()
                .filter { it != out && it.name.startsWith("client-") }
                .forEach { it.setWritable(true); it.delete() }
            dir.listFiles().orEmpty()
                .filter { it.name.startsWith("injected-client-") && !it.name.startsWith("injected-client-$version.jar") }
                .forEach { it.delete() }
            Log.i(TAG, "game client $version prepared in ${(System.nanoTime() - started) / 1_000_000} ms")
            return out
        }
        finally
        {
            work.deleteRecursively()
        }
    }

    /**
     * Appends the client's dex to the app's PathClassLoader — the technique the MultiDex support
     * library used: build a loader for the jar, then splice its dex elements onto the app loader's
     * element list. Classes then load with the app loader as their defining loader, so the client
     * resolves runelite-api / runelite-client / android-awt exactly as when it was in the APK.
     */
    private fun install(dexJar: File)
    {
        // The app loader has to open the dex itself: ART binds a DexFile to the first loader
        // that defines from it, so borrowing elements from a throwaway PathClassLoader died
        // with "Attempt to register dex file … with multiple class loaders".
        // BaseDexClassLoader.addDexPath does exactly this append (what MultiDex uses on API
        // 24+); it's hidden API, so HiddenApiBypass covers devices that refuse the reflection.
        val app = ClientPreparer::class.java.classLoader as dalvik.system.BaseDexClassLoader
        try
        {
            dalvik.system.BaseDexClassLoader::class.java
                .getDeclaredMethod("addDexPath", String::class.java)
                .apply { isAccessible = true }
                .invoke(app, dexJar.path)
        }
        catch (e: ReflectiveOperationException)
        {
            Log.w(TAG, "addDexPath via reflection refused, using HiddenApiBypass", e)
            org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(
                dalvik.system.BaseDexClassLoader::class.java, app, "addDexPath", dexJar.path)
        }
    }

    private fun get(url: String): ByteArray = open(url).let { c -> c.inputStream.use { it.readBytes() }.also { c.disconnect() } }

    private fun download(url: String, to: File, onProgress: (Long, Long) -> Unit)
    {
        val c = open(url)
        try
        {
            val total = c.contentLengthLong
            var done = 0L
            var lastPercent = -1L
            c.inputStream.use { input ->
                to.outputStream().buffered().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true)
                    {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val percent = if (total > 0) done * 100 / total else -1
                        if (percent != lastPercent) { lastPercent = percent; onProgress(done, total) }
                    }
                }
            }
        }
        finally
        {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection
    {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "RuneLite-Android")
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for $url")
        return c
    }

    private fun sha256(file: File): String
    {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true)
            {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private val DEX = Regex("""classes\d*\.dex""")
}
