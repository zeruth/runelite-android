// JVM harness: runs the same pre-D8 transform chain as ClientPreparer (+ optionally
// FixLockVerificationTask) and D8 on an injected-client jar. See run.sh.
//   Harness <injected-client.jar> <out-dex.jar> <with-fix: true|false> <workdir>
// Also prints the lock-structure problems (what ART's verifier would reject) before/after.
import net.runelite.mp.client.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile

fun report(label: String, jar: File) {
    var methods = 0
    var bad = 0
    JarFile(jar).use { j ->
        for (e in j.entries()) {
            if (!e.name.endsWith(".class")) continue
            val cn = ClassNode()
            ClassReader(j.getInputStream(e).readBytes()).accept(cn, ClassReader.SKIP_DEBUG)
            for (m in cn.methods) {
                if (m.instructions.toArray().none { it.opcode == Opcodes.MONITORENTER }) continue
                methods++
                val p = LockStructure.problems(m)
                if (p.isNotEmpty()) {
                    bad++
                    System.err.println("  [$label] ${cn.name}.${m.name}${m.desc}: ${p.size} problem(s), e.g. ${p.first()}")
                }
            }
        }
    }
    System.err.println("[$label] methods with monitorenter: $methods, failing lock structure: $bad")
}

fun main(args: Array<String>) {
    val raw = File(args[0])
    val out = File(args[1])
    val withFix = args[2] == "true"
    val work = File(args[3]).apply { mkdirs() }
    val prop = File(work, "1-prop.jar")
    RewriteLauncherEnvTask().apply {
        inputJar = raw; outputJar = prop; classEntry = "client.class"
        launcherKeys = listOf("JX_ACCESS_TOKEN", "JX_REFRESH_TOKEN", "JX_SESSION_ID", "JX_CHARACTER_ID", "JX_DISPLAY_NAME")
    }.run()
    val windowMode = File(work, "2-windowmode.jar")
    LocateWindowModeTask().apply { inputJar = prop; outputJar = windowMode }.run()
    val concat = File(work, "3-concat.jar")
    DesugarStringConcatTask().apply { inputJar = windowMode; outputJar = concat; helperOwner = "net/runelite/mp/util/IndyConcat" }.run()
    val noLambda = File(work, "4-nolambda.jar")
    DesugarLambdasTask().apply { inputJar = concat; outputJar = noLambda; syntheticPrefix = "runelite/desugar/Lambda" }.run()
    val apis = File(work, "5a-apis.jar")
    DesugarApisTask().apply { inputJar = noLambda; outputJar = apis }.run()
    var final = apis
    report("before", apis)
    if (withFix) {
        final = File(work, "5-final.jar")
        FixLockVerificationTask().apply { inputJar = apis; outputJar = final }.run()
        report("after", final)
    }
    val dexOut = File(work, "dex-" + (if (withFix) "fixed" else "baseline")).apply { deleteRecursively(); mkdirs() }
    val errors = mutableListOf<String>()
    com.android.tools.r8.D8.run(
        com.android.tools.r8.D8Command.builder(object : com.android.tools.r8.DiagnosticsHandler {
            override fun error(error: com.android.tools.r8.Diagnostic) { errors += error.diagnosticMessage }
            override fun warning(warning: com.android.tools.r8.Diagnostic) {}
        }).addProgramFiles(final.toPath()).setMinApiLevel(26)
            .setMode(com.android.tools.r8.CompilationMode.RELEASE)
            .setOutput(dexOut.toPath(), com.android.tools.r8.OutputMode.DexIndexed).build()
    )
    check(errors.isEmpty()) { "D8: ${errors.first()}" }
    java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zos ->
        for (f in dexOut.listFiles()!!.sortedBy { it.name }) {
            zos.putNextEntry(java.util.zip.ZipEntry(f.name)); zos.write(f.readBytes()); zos.closeEntry()
        }
    }
    System.err.println("wrote $out")
}
