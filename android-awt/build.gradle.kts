import com.nishtahir.CargoBuildTask
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.jar.JarFile

buildscript {
    dependencies {
        classpath("org.ow2.asm:asm:9.8")
    }
}

plugins {
    alias(libs.plugins.android.library)
    // Kotlin is compiled by AGP itself from 9.0 on (built-in Kotlin); the kotlin-android plugin
    // no longer applies.
    alias(libs.plugins.rust.android)
}

// The whole point of this module is to shadow java.awt.* at dex time so AWT-using code
// (notably RuneLite) can run on Android. That trick only survives D8 / the bootclasspath
// duplicate-class check when the bytecode is Java 8, so the source/target versions here
// are deliberately strict — do not bump them.
android {
    namespace = "net.runelite.awt"
    compileSdk = 37

    defaultConfig {
        // Lowered from 30 to match consumers; D8 still desugars our own bytecode for
        // anything below the dex API level.
        minSdk = 26
        lint.targetSdk = 37
        // Propagate keep rules for java.awt.* / javax.swing.* / sun.* to any consuming
        // app's R8 step. The gamepack calls these by name; renaming them at link time
        // breaks Class.forName / reflection lookups inside the obfuscated client jar.
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
                "x86"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


    ndkVersion = "28.2.13676358"

    lint {
        // Surface anything in the injected jar that references desktop-only classes we
        // haven't yet shadowed — InvalidPackage is what catches it.
        checkDependencies = true
        error.add("InvalidPackage")
        // Locale + InlinedApi we either fix at the call site or guard with SDK_INT.
    }
}

// Pin the injected RuneLite jar onto the compile classpath (not packaged). This is what
// lets lint walk every reference it makes and flag any class we still need to shadow.
// Built-in Kotlin defaults jvmTarget to compileOptions.targetCompatibility, but the shadow
// contract above is the whole module, so pin it explicitly all the same.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

val injectedJars = rootProject.fileTree("data") {
    include("runelite-*-injected-*-clean.jar")
}
dependencies {
    implementation("androidx.graphics:graphics-path:1.0.1")
    compileOnly(injectedJars)
}

// common.settings.gradle.kts pins JavaCompile to --release=11; AGP rejects --release at
// all (it needs to own the bootclasspath) and we also need Java 8 here regardless.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

// The shadow tree follows OpenJDK's style (4-space indents, K&R braces); skip the
// repo-wide checkstyle config that's tuned for the RuneLite client's Java.
tasks.withType<Checkstyle>().configureEach { enabled = false }

cargo {
    module = "./rust"
    libname = "runelite_awt_native"
    // Add "arm" and "x86" once `rustup target add armv7-linux-androideabi i686-linux-android` is run.
    targets = listOf(
        "arm64",  // arm64-v8a (64-bit ARM)
        "arm",    // armeabi-v7a (32-bit ARM)
        "x86_64", // x86_64 emulator
        "x86"     // x86 emulator
    )
    profile = "release"
    pythonCommand = "python"
}

// rust-android-gradle doesn't hook itself into AGP's variant pipeline automatically;
// we need to add cargoBuild as a dependency of merge[Variant]JniLibFolders.
val awtNativeDeclarations = layout.projectDirectory
    .file("src/main/kotlin/net/runelite/awt/AwtNative.kt").asFile

// Where cargoBuild drops the built .so per ABI. rust-android-gradle already adds this as
// a jniLibs source folder — adding it again produces "Duplicate resources" — but AGP's
// merge task does not pick it up for up-to-date checking, so `dependsOn` alone gets you
// correct *ordering* and a permanently stale *result*: cargoBuild recompiles, the merge
// reports UP-TO-DATE, and the APK keeps whatever .so it packaged the first time.
//
// That is not hypothetical. fillOval/drawOval/fillRoundRect were added to rust/src and
// AwtNative.kt on 2026-08-24 and never reached a build; every APK for the next two weeks
// shipped the library from 2026-08-19, and the first overlay to draw an ellipse died with
// `UnsatisfiedLinkError: No implementation found for ... AwtNative.fillOval`. Declaring
// the directory as an explicit input is what makes the merge notice.
val rustJniLibs = layout.buildDirectory.dir("rustJniLibs/android")

tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    dependsOn(tasks.withType<CargoBuildTask>())
    inputs.dir(rustJniLibs)
        .withPropertyName("rustJniLibs")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
        .optional(true)

    // Fail the build if the packaged library doesn't export every native method
    // AwtNative.kt declares. A missing symbol is otherwise invisible until the exact
    // draw call that needs it runs, which can be deep into a play session and reads as a
    // crash in whichever overlay happened to trigger it.
    val mergedOutput = outputs.files
    val declarationSource = awtNativeDeclarations
    doLast {
        val declared = Regex("""external\s+fun\s+(\w+)""")
            .findAll(declarationSource.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        if (declared.isEmpty()) return@doLast

        val libs = mergedOutput.asFileTree
            .matching { include("**/librunelite_awt_native.so") }
            .files
        require(libs.isNotEmpty()) {
            "No librunelite_awt_native.so in the merged JNI libs — cargoBuild produced nothing."
        }
        for (lib in libs) {
            // ISO-8859-1 round-trips arbitrary bytes, and the symbol names are ASCII.
            val text = String(lib.readBytes(), Charsets.ISO_8859_1)
            val missing = declared.filterNot { text.contains("Java_net_runelite_awt_AwtNative_$it") }
            require(missing.isEmpty()) {
                "${lib.parentFile.name}/${lib.name} is missing JNI symbol(s) $missing declared in " +
                    "AwtNative.kt. The packaged native library is out of date with rust/src — " +
                    "check that cargoBuild ran and that its output is a jniLibs source dir."
            }
        }
        logger.lifecycle(
            "verified ${declared.size} AwtNative JNI symbol(s) across ${libs.size} ABI(s)"
        )
    }
}

// Bytecode-scan the injected RuneLite jar for every java.awt.* reference, then diff
// against the shadow classes present in this module. Output is the surface we still need
// to implement (or stub) — drives this module's incremental porting.
//
// Defaults to the newest .../data/*-injected-*-clean.jar if -PinjectedJar isn't passed.
tasks.register("awtSurfaceReport") {
    group = "verification"
    description = "List desktop-only classes the injected RuneLite jar + runelite-api + runelite-client reference but this module does not yet shadow."

    val explicitJar = (findProperty("injectedJar") as? String)?.let { rootProject.file(it) }
    val javaRoot = layout.projectDirectory.dir("src/main/java").asFile
    val showMembers = (findProperty("members") as? String)?.toBoolean() == true
    val classFilter = (findProperty("classFilter") as? String)

    doLast {
        val sources = mutableListOf<File>()

        // 1. Injected RS client jar (auto-detected or -PinjectedJar=...)
        if (explicitJar != null) {
            sources.add(explicitJar)
        } else {
            val dataDir = rootProject.layout.projectDirectory.dir("data").asFile
            val candidates = (dataDir.listFiles { f -> f.name.matches(Regex("runelite-.*-injected-.*-clean\\.jar")) } ?: emptyArray())
                .sortedByDescending { it.lastModified() }
            if (candidates.isNotEmpty()) sources.add(candidates.first())
        }

        // 2. runelite-api + runelite-client jars (built from this repo). Android-awt
        //    eventually shims runelite-client, so its AWT/Swing/javax surface counts.
        rootProject.file("runelite-api/build/libs").listFiles { f ->
            f.name.matches(Regex("runelite-api-[\\d.\\-A-Za-z]+\\.jar")) && !f.name.contains("javadoc") && !f.name.contains("sources")
        }?.firstOrNull()?.let(sources::add)
        rootProject.file("runelite-client/build/libs").listFiles { f ->
            f.name.matches(Regex("client-[\\d.\\-A-Za-z]+\\.jar")) && !f.name.contains("javadoc") && !f.name.contains("sources") && !f.name.contains("shaded")
        }?.firstOrNull()?.let(sources::add)

        require(sources.isNotEmpty()) { "No source jars found to scan." }

        val collector = AwtRefCollector()
        for (src in sources) {
            JarFile(src).use { jf ->
                jf.stream()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        jf.getInputStream(entry).use { stream ->
                            val cr = ClassReader(stream)
                            cr.accept(collector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                        }
                    }
            }
        }
        val jar = sources.first()

        val present = sortedSetOf<String>().apply {
            if (javaRoot.isDirectory) {
                javaRoot.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .forEach { f ->
                        val rel = f.relativeTo(javaRoot).invariantSeparatorsPath
                        add(rel.removeSuffix(".java"))
                    }
            }
        }

        // Nested classes (Foo$Bar) are satisfied by their enclosing .java file existing.
        val missing = collector.classes.filter { name ->
            if (name in present) false
            else {
                val outer = name.substringBefore('$')
                outer !in present
            }
        }.toSortedSet()
        println("== AWT surface report ==")
        println("Source jars:")
        sources.forEach { println("  ${it.absolutePath}") }
        println("Referenced:       ${collector.classes.size} desktop-only classes")
        println("Already shadowed: ${present.size}")
        println("Missing:          ${missing.size}")
        if (missing.isNotEmpty()) {
            println()
            println("-- Missing classes --")
            missing.forEach { println("  $it") }
        }
        val unused = present - collector.classes
        if (unused.isNotEmpty()) {
            println()
            println("-- Shadowed but unreferenced (candidates for removal) --")
            unused.forEach { println("  $it") }
        }

        if (showMembers) {
            println()
            println("== Member-level usage ==")
            val byOwner = collector.members.groupBy { it.owner }.toSortedMap()
            byOwner.forEach { (owner, refs) ->
                if (classFilter != null && !owner.contains(classFilter)) return@forEach
                println()
                println(owner)
                refs.map { "  ${it.kind} ${it.name}${it.descriptor}" }
                    .toSortedSet()
                    .forEach { println(it) }
            }
        } else {
            println()
            println("(Re-run with -Pmembers=true [-PclassFilter=Color] to see method+field references.)")
        }
    }
}

private data class AwtMemberRef(val owner: String, val kind: String, val name: String, val descriptor: String)

private val DESKTOP_PREFIXES = listOf(
    "java/applet/",
    "java/awt/",
    "java/beans/",
    "javax/sound/",
    "javax/imageio/",
    "javax/swing/",
    "javax/print/",
    "sun/awt/",
    "sun/font/",
    "sun/java2d/",
    "com/sun/awt/",
    "com/sun/imageio/",
)

private fun isDesktopOnly(internalName: String?): Boolean {
    if (internalName == null) return false
    return DESKTOP_PREFIXES.any { internalName.startsWith(it) }
}

private class AwtRefCollector : ClassVisitor(Opcodes.ASM9) {
    val classes: MutableSet<String> = sortedSetOf()
    val members: MutableList<AwtMemberRef> = mutableListOf()

    private fun record(internalName: String?) {
        if (internalName == null) return
        val n = if (internalName.startsWith("[")) {
            Type.getType(internalName).elementType.let { if (it.sort == Type.OBJECT) it.internalName else null }
        } else internalName
        if (n != null && isDesktopOnly(n)) classes.add(n)
    }
    private fun recordType(desc: String?) {
        if (desc == null) return
        try {
            val t = Type.getType(desc)
            when (t.sort) {
                Type.OBJECT -> record(t.internalName)
                Type.ARRAY -> if (t.elementType.sort == Type.OBJECT) record(t.elementType.internalName)
                Type.METHOD -> {
                    recordType(t.returnType.descriptor)
                    t.argumentTypes.forEach { recordType(it.descriptor) }
                }
            }
        } catch (_: IllegalArgumentException) {
        }
    }
    private fun recordMember(kind: String, owner: String?, name: String?, descriptor: String?) {
        if (owner == null || name == null || descriptor == null) return
        if (!isDesktopOnly(owner)) return
        members.add(AwtMemberRef(owner, kind, name, descriptor))
    }

    override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
        record(superName)
        interfaces?.forEach { record(it) }
    }

    override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor? {
        recordType(descriptor)
        return null
    }

    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        recordType(descriptor)
        exceptions?.forEach { record(it) }
        return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitTypeInsn(opcode: Int, type: String?) { record(type) }
            override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
                record(owner); recordType(descriptor); recordMember("FIELD", owner, name, descriptor)
            }
            override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
                record(owner); recordType(descriptor); recordMember("METHOD", owner, name, descriptor)
            }
            override fun visitLdcInsn(value: Any?) {
                if (value is Type) recordType(value.descriptor)
            }
            override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
                recordType(descriptor)
            }
            override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?, start: org.objectweb.asm.Label?, end: org.objectweb.asm.Label?, index: Int) {
                recordType(descriptor)
            }
        }
    }
}
