import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

// Android target is opt-in: it requires the Android SDK plus either ANDROID_HOME (or
// ANDROID_SDK_ROOT) or an sdk.dir entry in local.properties. When opted in, you also
// need to widen gradle/verification-metadata.xml to cover AGP's transitive buildscript
// deps (netty, protobuf, tink, jaxb, slf4j, etc).
//
// The Compose Desktop target builds without any Android tooling, so the rest of the
// repo configures cleanly when the SDK isn't installed.
val androidSdkAvailable: Boolean = run {
    val envSdk = System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
        ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
    val localProps = rootProject.file("local.properties").takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    envSdk != null || localProps?.getProperty("sdk.dir")?.isNotBlank() == true
}

buildscript {
    val androidEnabled = System.getenv("ANDROID_HOME")?.isNotBlank() == true
        || System.getenv("ANDROID_SDK_ROOT")?.isNotBlank() == true
        || rootProject.file("local.properties").let { it.isFile && it.readText().contains("sdk.dir=") }
    if (androidEnabled) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // Used by the desugar tasks below. AGP and the Firebase plugins come from the root
            // project's plugins { } block (version catalog), not from here: this classpath used
            // to pin a second AGP version alongside the catalog's.
            classpath("org.ow2.asm:asm:9.10.1")
            classpath("org.ow2.asm:asm-commons:9.8")
        }
    }
}

// A plain Android application module. Until AGP 9 this was a Kotlin Multiplatform module with
// an Android target and a one-file desktop preview; AGP 9 no longer allows kotlin.multiplatform
// and com.android.application in the same subproject, and the multiplatform half was only ever
// that preview, which now lives in :android-desktop. Compose comes from the Compose
// Multiplatform plugin's Android support; Kotlin is compiled by AGP (built-in Kotlin).
plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

if (androidSdkAvailable) {
    apply(plugin = "com.android.application")
    // Firebase wiring. Google Services plugin must come AFTER the Android plugin so it
    // can hook the variant pipeline; Crashlytics plugin attaches mapping.txt upload to
    // each release variant once R8 is on.
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// common.settings.gradle.kts forces options.release = 11 on every JavaCompile task.
// AGP refuses to coexist with --release (it needs to set the bootclasspath itself), so
// we strip it here and fall back to source/target compat = 17 for this module's Java
// tasks.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

// guava-23.2-jre ships ListenableFuture itself; the separate `listenablefuture:1.0`
// placeholder gets dragged in transitively by some runelite deps and collides at dex
// time. Drop the placeholder everywhere.
configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
    // runelite-client's runtimeOnly("net.runelite:injected-client:…") would drag in the
    // published Maven copy alongside our locally-desugared one. Drop the Maven one.
    exclude(group = "net.runelite", module = "injected-client")
    // rlawt ships JOGL-bound AWTContext native code that won't load on Android. We
    // provide our own pure-Java AWTContext under net.runelite.rlawt that delegates
    // to GlesHost; excluding the real artifact avoids a duplicate-class dex error.
    exclude(group = "net.runelite", module = "rlawt")
    // LWJGL ships native JOGL/OpenGL bindings — we provide pure-Java shim classes
    // under org.lwjgl.* that delegate to android.opengl.GLES32. Drop the real
    // artifact group-wide so the shim doesn't dex-collide with the upstream copy.
    exclude(group = "org.lwjgl")
}

val target = "runelite-1.13.0-injected-36433848015.271"

// --------------------------------------------------------------------------------------
// rewriteLauncherEnv: makes the injected client read its JX_* launcher credentials from
// system properties instead of the applet-parameter lookup it ships with.
//
// The stock client reads each of JX_ACCESS_TOKEN / JX_REFRESH_TOKEN / JX_SESSION_ID /
// JX_CHARACTER_ID / JX_DISPLAY_NAME in `client.init` as
//
//     ldc "JX_SESSION_ID"; invokestatic client.<obf>(Ljava/lang/String;)Ljava/lang/String;
//
// where `<obf>` resolves to the applet's getParameter — which returns null on Android,
// because there's no browser plugin handing us a <param> tag. RuneLiteLauncher instead
// seeds the values with System.setProperty before RuneLite.main runs, so each of those
// call sites is rewritten to java/lang/System.getProperty. Same descriptor, same stack
// effect, so nothing downstream has to be recomputed.
//
// This replaces what used to be a hand-patched `-prop` copy of the jar checked into
// data/; drop a new revision's jar in, point `target` at it, and the patch reapplies
// itself. The obfuscated helper is renamed every revision (it was `gt` in 1.12.37 and
// `lv` in 1.12.38), which is why the match is on the shape of the call rather than a name.
// --------------------------------------------------------------------------------------
val rewriteLauncherEnv = if (androidSdkAvailable) {
    tasks.register<RewriteLauncherEnvTask>("rewriteLauncherEnv") {
        inputJar.set(rootProject.file("data/$target.jar"))
        outputJar.set(layout.buildDirectory.file("desugared/injected-client-prop.jar"))
        classEntry.set("client.class")
        launcherKeys.set(
            listOf(
                "JX_ACCESS_TOKEN",
                "JX_REFRESH_TOKEN",
                "JX_SESSION_ID",
                "JX_CHARACTER_ID",
                "JX_DISPLAY_NAME",
            )
        )
    }
} else null

// --------------------------------------------------------------------------------------
// desugarStringConcat: rewrites invokedynamic `makeConcatWithConstants` sites into calls
// to net.runelite.mp.util.IndyConcat. D8 otherwise blows up on unusual constant types
// ProGuard puts in the recipe.
//
// desugarLambdas: rewrites invokedynamic `LambdaMetafactory.metafactory` sites into
// `new SyntheticLambda(...)`. Android marks LambdaMetafactory as a hidden API even on
// modern versions (where the class is physically present), so any indy that survives
// to runtime fails with NoSuchMethodError. D8 leaves indys intact at minSdk >= 26;
// this task pre-desugars them so we don't depend on runtime LambdaMetafactory at all.
// --------------------------------------------------------------------------------------
val desugarStringConcat = if (androidSdkAvailable) {
    tasks.register<DesugarStringConcatTask>("desugarStringConcat") {
        inputJar.set(rewriteLauncherEnv!!.flatMap { it.outputJar })
        outputJar.set(layout.buildDirectory.file("desugared/injected-client.jar"))
        helperOwner.set("net/runelite/mp/util/IndyConcat")
    }
} else null

val desugarLambdas = if (androidSdkAvailable) {
    tasks.register<DesugarLambdasTask>("desugarLambdas") {
        inputJar.set(desugarStringConcat!!.flatMap { it.outputJar })
        outputJar.set(layout.buildDirectory.file("desugared/injected-client-nolambda.jar"))
        syntheticPrefix.set("runelite/desugar/Lambda")
    }
} else null

val desugarApis = if (androidSdkAvailable) {
    tasks.register("desugarApis") {
        dependsOn(desugarLambdas!!)

        val input = desugarLambdas!!.flatMap { it.outputJar }
        val output = layout.buildDirectory.file("desugared/injected-client-final.jar")

        inputs.file(input)
        outputs.file(output)

        doLast {
            val inFile = input.get().asFile
            val outFile = output.get().asFile

            JarFile(inFile).use { srcJar ->
                JarOutputStream(outFile.outputStream()).use { outJar ->
                    val entries = srcJar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        srcJar.getInputStream(entry).use { stream ->
                            outJar.putNextEntry(JarEntry(entry.name))

                            if (entry.name.endsWith(".class")) {
                                val bytes = stream.readBytes()
                                val reader = org.objectweb.asm.ClassReader(bytes)
                                val writer = org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS)

                                // Direct ASM transformer to find and swap the method signature
                                val transformer = object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, writer) {
                                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): org.objectweb.asm.MethodVisitor {
                                        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                        return object : org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9, mv) {
                                            override fun visitMethodInsn(opcode: Int, owner: String?, nameStr: String?, desc: String?, isInterface: Boolean) {
                                                // Target the Java 9 Matcher.replaceAll(Function) signature
                                                if (opcode == org.objectweb.asm.Opcodes.INVOKEVIRTUAL &&
                                                    owner == "java/util/regex/Matcher" &&
                                                    nameStr == "replaceAll" &&
                                                    desc == "(Ljava/util/function/Function;)Ljava/lang/String;") {

                                                    // Reroute it to your Java 8 polyfill method
                                                    super.visitMethodInsn(
                                                        org.objectweb.asm.Opcodes.INVOKESTATIC,
                                                        "net/runelite/mp/util/IndyConcat",
                                                        "replaceAllJava8",
                                                        "(Ljava/util/regex/Matcher;Ljava/util/function/Function;)Ljava/lang/String;",
                                                        false
                                                    )
                                                }
                                                // 2. interceptor for MethodHandles.privateLookupIn
                                                else if (opcode == org.objectweb.asm.Opcodes.INVOKESTATIC &&
                                                    owner == "java/lang/invoke/MethodHandles" &&
                                                    nameStr == "privateLookupIn" &&
                                                    desc == "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles\$Lookup;)Ljava/lang/invoke/MethodHandles\$Lookup;") {

                                                    super.visitMethodInsn(
                                                        org.objectweb.asm.Opcodes.INVOKESTATIC,
                                                        "net/runelite/mp/util/IndyConcat",
                                                        "privateLookupInJava8", // Reroutes to your Java 8 reflection workaround
                                                        "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles\$Lookup;)Ljava/lang/invoke/MethodHandles\$Lookup;",
                                                        false
                                                    )
                                                } else {
                                                    super.visitMethodInsn(opcode, owner, nameStr, desc, isInterface)
                                                }
                                            }
                                        }
                                    }
                                }

                                // SKIP_DEBUG eliminates the malformed tables causing D8/R8 to throw NullPointerExceptions
                                reader.accept(transformer, org.objectweb.asm.ClassReader.SKIP_DEBUG)
                                outJar.write(writer.toByteArray())
                            } else {
                                stream.copyTo(outJar)
                            }
                        }
                    }
                }
            }
        }
    }
} else null


configurations.all {
    resolutionStrategy.force(
        "com.google.guava:guava:33.6.0-android",
        // Guava's own placeholder for the ListenableFuture split artifact. AGP 9 no longer adds
        // dependency constraints (android.dependency.useConstraints defaults to false), so the
        // real listenablefuture:1.0 some transitive dependency asks for surfaced as a duplicate
        // class against guava-android; pinning the empty version is Guava's documented answer.
        "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava"
    )
}

// Built-in Kotlin takes its jvmTarget from android.compileOptions.targetCompatibility (17 below),
// so there is no kotlin { } block to configure here.

if (androidSdkAvailable) {
    // The android plugin is applied above by name (only when an SDK is present), so the typed
    // implementation() accessor does not exist at script-compile time; use the configuration name.
    dependencies {
        "implementation"(compose.runtime)
        "implementation"(compose.foundation)
        "implementation"(compose.material3)
        "implementation"(compose.ui)
        "implementation"(compose.components.resources)
        "implementation"(libs.androidx.activity.compose)
        // Firebase. BoM coordinates the individual product versions so they
        // can't drift apart. Crashlytics auto-installs an uncaught handler on
        // first FirebaseApp init; we still front it with AndroidCrashReporter
        // to add Compose-state breadcrumbs that Crashlytics can't see.
        //
        // KotlinDependencyHandler.platform() doesn't accept the catalog's
        // Provider<MinimalExternalModuleDependency> directly, so route
        // through the project's standard DependencyHandler which does.
        "implementation"(platform(libs.firebase.bom))
        "implementation"(libs.firebase.crashlytics)
        "implementation"(libs.firebase.analytics)
        // android-awt provides the shadow java.awt / javax.swing / javax.sound surface
        // the runelite jars compile against; it must be on the classpath before
        // anything that touches AWT can resolve at dex time.
        "implementation"(project(":android-awt"))
        "implementation"(project(":jshell"))
        "implementation"(project(":client"))
        "implementation"("net.runelite:runelite-api:${project.version}")
        "implementation"(libs.rs.cache)
        // FlatLaf jars are pure Java; classes load fine on Android, even though
        // the actual Swing rendering pipeline behind them won't.
        "implementation"(libs.flatlaf.core)
        "implementation"(libs.flatlaf.extras)
        // Plugin-hub plugins shade their `implementation` deps into the desktop
        // jar, but the Android dex pipeline ships thin jars (plugin classes only),
        // so those bundled libs are missing at runtime. The DexClassLoader's parent
        // is the host APK, so providing the shared ones here resolves them for every
        // plugin. tomlj (TOML parser, pulls antlr4-runtime transitively) is what the
        // resource-packs plugin needs — without it it dies at injector creation with
        // NoClassDefFoundError: org/tomlj/TomlTable.
        "implementation"("org.tomlj:tomlj:1.1.1")
        // Patched RS client (RuneLite's injected-client artifact). The raw jar has
        // invokedynamic makeConcatWithConstants sites whose recipes use constant
        // types D8's StringConcat desugarer rejects, so we pre-rewrite them to
        // calls into IndyConcat at build time. See desugarStringConcat below.
        "implementation"(files(desugarApis))

        "implementation"("com.google.guava:guava:33.6.0-android")
    }
}

// AGP's `coreLibraryDesugaring` configuration is set up by the android plugin after the
// KMP `kotlin {}` block runs, so add the polyfill dep here outside the source-set
// closures. desugar_jdk_libs 2.1.x is the version that ships the LambdaMetafactory
// polyfill alongside the rest of the modern JDK surface.
if (androidSdkAvailable) {
    dependencies {
        add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.4")
        // Lombok — 117HD's java sources use @Slf4j / @Getter / @AllArgsConstructor.
        // Wired here because KMP source-set closures don't expose `annotationProcessor`;
        // AGP creates the config for the android variant.
        add("compileOnly", "org.projectlombok:lombok:1.18.30")
        add("annotationProcessor", "org.projectlombok:lombok:1.18.30")
    }
}

if (androidSdkAvailable) {
    // AGP 9's public DSL type; BaseAppModuleExtension was the pre-newDsl internal one.
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        namespace = "net.runelite.mp"
        compileSdk = 37

        defaultConfig {
            applicationId = "net.runelite.mp"
            buildConfigField("String", "PLUGIN_HUB_VERSION", "\"${providers.gradleProperty("androidPluginHubVersion").get()}\"")
            // minSdk = 26 is the floor flatlaf/jsvg require. Lambda invokedynamics from
            // older bytecode levels still hit "LambdaMetafactory.metafactory" hidden-API
            // failures at runtime — TODO: pre-process the injected + runelite-client jars
            // with an ASM lambda-desugar pass (mirror of desugarStringConcat) so the indys
            // become synthetic classes. Until that lands, lambda-heavy callsites in the
            // injected client crash on first invocation.
            minSdk = 26
            //noinspection EditedTargetSdkVersion
            targetSdk = 37
            // Derive versionCode from the dotted project version so each RuneLite cycle
            // (1.12.27 → 1.12.28 → 1.13.0 → …) lands a monotonically-increasing integer
            // without manual bumping. Encoding: major*1_000_000 + minor*1_000 + patch,
            // so 1.12.27 → 1_012_027. Max minor/patch are 999 each — comfortable headroom
            // versus how OSRS-RuneLite actually versions. Monotonic across any of
            // {patch ↑, minor ↑ with patch reset, major ↑ with minor+patch reset}.
            val versionMajor = 1
            val versionMinor = 0
            val versionPatch = 29
            versionCode = (versionMajor * 1_000_000) + (versionMinor * 1_000) + versionPatch
            versionName = project.version.toString()
            // Anti-tamper hook. SignatureGuard reads this field at MainActivity init and
            // refuses to run when the on-device APK's signing SHA-256 doesn't match.
            // Default is empty (= skip), so debug builds and release-with-debug-fallback
            // both run. Release with a real keystore overwrites this with the cert SHA
            // computed below — no hardcoded hash to drift on key rotation.
            buildConfigField("String", "EXPECTED_SIGNING_SHA256", "\"\"")

            ndk {
                abiFilters += listOf(
                    "arm64-v8a",
                    "armeabi-v7a",
                    "x86_64",
                    "x86"
                )
            }
        }

        buildFeatures {
            // Required so SignatureGuard can read EXPECTED_SIGNING_SHA256 from
            // BuildConfig at runtime.
            buildConfig = true
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            // Polyfills java.lang.invoke.LambdaMetafactory (a hidden API on stock Android
            // despite physically existing in core-oj.jar) plus the rest of the modern JDK
            // surface RuneLite uses (java.time, java.util.stream backports, etc).
            isCoreLibraryDesugaringEnabled = true
        }

        // The source tree keeps its KMP-era src/androidMain layout; only the build no longer
        // treats the module as multiplatform, so every directory is mapped onto "main" here.
        sourceSets.named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            java.srcDirs("src/androidMain/kotlin", "src/androidMain/java")
            kotlin.srcDirs("src/androidMain/kotlin")
            res.srcDirs("src/androidMain/res")
            assets.srcDirs("src/androidMain/assets")
            resources.srcDirs("src/androidMain/resources")
        }

        // Release signing.
        //
        // Production builds read keystore path + passwords + alias from local.properties
        // (already gitignored via the SDK-dir line at .gitignore:8). When any of the
        // four keys is missing — common in dev iteration, CI without secrets, or first
        // checkout — we silently fall back to the debug keystore so the build still
        // produces an installable APK; logs identify which path was taken.
        //
        // Setup (one-time, per developer):
        //
        //   1. Generate a keystore. Stay OUT of the repo tree so a stray `git clean -fd`
        //      can't wipe your update channel:
        //
        //          keytool -genkeypair -v \
        //              -keystore "$HOME/keystores/runelite-mp-release.jks" \
        //              -storetype JKS \
        //              -keyalg RSA -keysize 4096 -validity 9125 \
        //              -alias runelite-mp
        //
        //      (9125 days = 25 years — Play Store requires the key be valid until at
        //      least Oct 22, 2033, and longer doesn't hurt. Use distinct passwords for
        //      the store and the key.)
        //
        //   2. Add to local.properties (path can be absolute or relative-to-rootProject):
        //
        //          runeliteMp.signing.storeFile=/Users/<you>/keystores/runelite-mp-release.jks
        //          runeliteMp.signing.storePassword=<store password>
        //          runeliteMp.signing.keyAlias=runelite-mp
        //          runeliteMp.signing.keyPassword=<key password>
        //
        //   3. Back up the keystore out-of-band (1Password, encrypted USB, whatever).
        //      Losing it permanently breaks the update channel for net.runelite.mp —
        //      Play Store rejects any future APK signed with a different key.
        val signingProps = rootProject.file("local.properties").takeIf { it.isFile }?.let { f ->
            Properties().apply { f.inputStream().use { load(it) } }
        } ?: Properties()
        val releaseStoreFile = signingProps.getProperty("runeliteMp.signing.storeFile")
        val releaseStorePassword = signingProps.getProperty("runeliteMp.signing.storePassword")
        val releaseKeyAlias = signingProps.getProperty("runeliteMp.signing.keyAlias")
        val releaseKeyPassword = signingProps.getProperty("runeliteMp.signing.keyPassword")
        val haveReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
            .all { !it.isNullOrBlank() }
            && releaseStoreFile?.let { rootProject.file(it).isFile } == true

        signingConfigs {
            getByName("debug") {
                // AGP creates a debug keystore at ~/.android/debug.keystore on first
                // build; nothing extra to do here.
            }
            if (haveReleaseSigning) {
                create("release") {
                    storeFile = rootProject.file(releaseStoreFile!!)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            }
        }

        // Compute the SHA-256 of the release signing certificate at configure time so
        // SignatureGuard can hard-fail any APK that was re-signed by a third party.
        // Derived from the same keystore we're about to sign with — no hardcoded hash
        // to drift if you ever rotate keys. Empty when we fall back to debug signing.
        val expectedReleaseSigSha = if (haveReleaseSigning) {
            try {
                val ks = KeyStore.getInstance("JKS")
                rootProject.file(releaseStoreFile!!).inputStream().use {
                    ks.load(it, releaseStorePassword!!.toCharArray())
                }
                val cert = ks.getCertificate(releaseKeyAlias!!) as X509Certificate
                val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                digest.joinToString("") { b -> "%02x".format(b) }
            } catch (e: Throwable) {
                logger.warn("runelite-mp release: failed to read signing cert (${e.message}); disabling SignatureGuard")
                ""
            }
        } else ""

        buildTypes.named("release") {
            // R8 + resource shrinking. Crashlytics gradle plugin auto-uploads
            // mapping.txt to Firebase so stack traces deobfuscate in the console.
            //
            // The optimizer is kept OFF (-dontoptimize in proguard-rules.pro; the conservative
            // proguard-android.txt default that used to carry it no longer exists in AGP 9)
            // because the optimize-enabled defaults include class merging and method
            // hoisting that collapse anonymous TypeLiteral / TypeToken subclasses into
            // their enclosing class. Guice and Gson both rely on those anonymous
            // classes' generic supertype Signature to read type arguments at runtime;
            // when R8 merges them away, getGenericSuperclass() returns a plain Class
            // instead of ParameterizedType and Key construction blows up at injector
            // creation. The conservative default still does name obfuscation + dead
            // code elimination — we just give up the marginal optimizer wins.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // AGP 9 no longer ships proguard-android.txt. The -optimize file is the same
                // default set without -dontoptimize; proguard-rules.pro adds -dontoptimize back, so
                // the optimizer stays off for the reason above.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (haveReleaseSigning) {
                logger.lifecycle("runelite-mp release: signing with $releaseStoreFile (alias=$releaseKeyAlias)")
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle("runelite-mp release: no production keystore in local.properties; signing with debug key")
                signingConfigs.getByName("debug")
            }
            // Overwrite the default empty hash with the actual prod cert SHA-256.
            // SignatureGuard inside the APK reads this and refuses to boot if the
            // installed APK's signing key doesn't match (e.g. someone disassembled,
            // patched, and re-signed it with a different cert).
            buildConfigField("String", "EXPECTED_SIGNING_SHA256", "\"1A207D8B2E29791E56E1750EDE6A32CA56AA10FB7AD36DA1E3DDF12B7BFB8E7E\"")
        }
        // Debug builds normally set debuggable=true which enables CheckJNI — wraps every
        // JNI call (AwtNative.blit/fillRect, Float.floatToRawIntBits, etc.) in validation
        // that costs ~30% of JNI time, and disables JIT intrinsics. simpleperf already
        // works via `<profileable>` in the manifest, so we don't need debuggable for perf
        // testing. ~22% JNI trampoline in the in-game profile is mostly this overhead.
        buildTypes.named("debug") {
            isDebuggable = false
            isJniDebuggable = false
        }

        // Many of runelite-client's transitive jars (jna, netty, slf4j, lwjgl, …) ship the
        // same META-INF housekeeping files and per-OS native blobs. Drop everything that's
        // either redundant or only useful on desktop OSes.
        packaging.resources.excludes += listOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/NOTICE.md",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-*",
            "META-INF/NOTICE-*",
            "META-INF/INDEX.LIST",
            "META-INF/DEPENDENCIES",
            "META-INF/MANIFEST.MF",
            "META-INF/maven/**",
            "META-INF/proguard/**",
            "META-INF/services/**",
            "META-INF/versions/**",
            "META-INF/*.kotlin_module",
            "META-INF/io.netty.versions.properties",
            "META-INF/native-image/**",
            "META-INF/native/**",
            "META-INF/windows/**",
            "META-INF/linux/**",
            "META-INF/macos/**",
            "META-INF/*.RSA",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "**/*.dll",
            "**/*.dylib",
            "**/*.dll.sha1",
            "**/*.so.sha1",
            "**/*.dylib.sha1",
            "google/protobuf/**",
        )
    }
}

// Implementation class for rewriteLauncherEnv — registered above.

abstract class RewriteLauncherEnvTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputJar: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputJar: org.gradle.api.file.RegularFileProperty

    /** Jar entry to patch. Every other entry is copied through unchanged. */
    @get:org.gradle.api.tasks.Input
    abstract val classEntry: org.gradle.api.provider.Property<String>

    /** Launcher credential names whose lookups get redirected to System.getProperty. */
    @get:org.gradle.api.tasks.Input
    abstract val launcherKeys: org.gradle.api.provider.ListProperty<String>

    private companion object {
        const val GETTER_DESC = "(Ljava/lang/String;)Ljava/lang/String;"
        const val SYSTEM = "java/lang/System"
    }

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val inFile = inputJar.get().asFile
        val outFile = outputJar.get().asFile
        val entryName = classEntry.get()
        val keys = launcherKeys.get().toSet()
        outFile.parentFile.mkdirs()

        var patched = false
        val rewrittenKeys = sortedSetOf<String>()
        val touchedMethods = linkedSetOf<String>()

        JarFile(inFile).use { input ->
            JarOutputStream(outFile.outputStream().buffered()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(JarEntry(entry.name))
                    if (entry.name == entryName) {
                        patched = true
                        output.write(rewrite(bytes, keys, rewrittenKeys, touchedMethods))
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
            }
        }

        // Both checks below are hard failures rather than warnings: an unpatched jar boots
        // fine and only falls over at the login screen, which is a miserable thing to
        // debug. If a revision changes the shape of these call sites, the build is what
        // should tell you.
        require(patched) { "rewriteLauncherEnv: no '$entryName' entry in ${inFile.name}" }
        val missing = keys - rewrittenKeys
        require(missing.isEmpty()) {
            "rewriteLauncherEnv: nothing rewritten for ${missing.sorted()} in $entryName. " +
                "The client's credential reads changed shape this revision — disassemble " +
                "client.init and re-check the ldc + invokestatic pattern."
        }
        logger.lifecycle(
            "rewriteLauncherEnv: redirected launcher credential lookups to " +
                "System.getProperty in $entryName — keys ${rewrittenKeys.toList()}, " +
                "methods ${touchedMethods.toList()}"
        )
    }

    /**
     * Rewrites `ldc "<KEY>"` + `invokestatic <obf>(String)String` into
     * `ldc "<KEY>"` + `invokestatic java/lang/System.getProperty(String)String`.
     *
     * The two instructions have to be adjacent, so any instruction or label in between
     * clears the pending match — otherwise a stale key could latch onto an unrelated
     * `(String)String` static further down the method. Copying the reader's constant pool
     * (`ClassWriter(cr, 0)`) leaves the rest of the class alone and skips frame and
     * max-stack recomputation, which ASM couldn't do here anyway: the injected jar
     * references obfuscated types that aren't loadable from the buildscript classloader.
     * The substitution is stack-neutral, so there's nothing to recompute.
     */
    private fun rewrite(
        bytes: ByteArray,
        keys: Set<String>,
        rewrittenKeys: MutableSet<String>,
        touchedMethods: MutableSet<String>,
    ): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(cr, 0)
        cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                return object : MethodVisitor(Opcodes.ASM9, delegate) {
                    private var pendingKey: String? = null

                    override fun visitLdcInsn(value: Any?) {
                        super.visitLdcInsn(value)
                        pendingKey = (value as? String)?.takeIf { it in keys }
                    }

                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        mName: String,
                        mDescriptor: String,
                        isInterface: Boolean,
                    ) {
                        val key = pendingKey
                        pendingKey = null
                        if (key != null &&
                            opcode == Opcodes.INVOKESTATIC &&
                            mDescriptor == GETTER_DESC &&
                            owner != SYSTEM
                        ) {
                            rewrittenKeys.add(key)
                            touchedMethods.add(name)
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SYSTEM, "getProperty", GETTER_DESC, false)
                            return
                        }
                        super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface)
                    }

                    // Anything else between the ldc and the call breaks the pattern.
                    override fun visitInsn(opcode: Int) {
                        pendingKey = null
                        super.visitInsn(opcode)
                    }

                    override fun visitIntInsn(opcode: Int, operand: Int) {
                        pendingKey = null
                        super.visitIntInsn(opcode, operand)
                    }

                    override fun visitVarInsn(opcode: Int, varIndex: Int) {
                        pendingKey = null
                        super.visitVarInsn(opcode, varIndex)
                    }

                    override fun visitTypeInsn(opcode: Int, type: String) {
                        pendingKey = null
                        super.visitTypeInsn(opcode, type)
                    }

                    override fun visitFieldInsn(opcode: Int, owner: String, fName: String, fDescriptor: String) {
                        pendingKey = null
                        super.visitFieldInsn(opcode, owner, fName, fDescriptor)
                    }

                    override fun visitInvokeDynamicInsn(
                        iName: String,
                        iDescriptor: String,
                        handle: Handle,
                        vararg args: Any?,
                    ) {
                        pendingKey = null
                        super.visitInvokeDynamicInsn(iName, iDescriptor, handle, *args)
                    }

                    override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) {
                        pendingKey = null
                        super.visitJumpInsn(opcode, label)
                    }

                    override fun visitLabel(label: org.objectweb.asm.Label) {
                        pendingKey = null
                        super.visitLabel(label)
                    }

                    override fun visitIincInsn(varIndex: Int, increment: Int) {
                        pendingKey = null
                        super.visitIincInsn(varIndex, increment)
                    }

                    override fun visitTableSwitchInsn(
                        min: Int,
                        max: Int,
                        dflt: org.objectweb.asm.Label,
                        vararg labels: org.objectweb.asm.Label,
                    ) {
                        pendingKey = null
                        super.visitTableSwitchInsn(min, max, dflt, *labels)
                    }

                    override fun visitLookupSwitchInsn(
                        dflt: org.objectweb.asm.Label,
                        switchKeys: IntArray?,
                        labels: Array<out org.objectweb.asm.Label>?,
                    ) {
                        pendingKey = null
                        super.visitLookupSwitchInsn(dflt, switchKeys, labels)
                    }

                    override fun visitMultiANewArrayInsn(arrayDescriptor: String, numDimensions: Int) {
                        pendingKey = null
                        super.visitMultiANewArrayInsn(arrayDescriptor, numDimensions)
                    }
                }
            }
        }, 0)
        return cw.toByteArray()
    }
}

// Implementation classes for desugarStringConcat — registered above.

abstract class DesugarStringConcatTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputJar: org.gradle.api.file.RegularFileProperty
    @get:org.gradle.api.tasks.OutputFile
    abstract val outputJar: org.gradle.api.file.RegularFileProperty
    @get:org.gradle.api.tasks.Input
    abstract val helperOwner: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val inFile = inputJar.get().asFile
        val outFile = outputJar.get().asFile
        outFile.parentFile.mkdirs()
        var classesProcessed = 0
        var sitesRewritten = 0
        var classesSkipped = 0
        // runelite-api ships its own canonical OAuthApi; the injected client bundles
        // a duplicate that breaks dex merging. Only OAuthApi conflicts — other
        // com/jagex/oldscape/pub/ types (OtlTokenRequester, etc.) exist ONLY in the
        // injected jar, so don't drop the whole package.
        val skipExact = setOf("com/jagex/oldscape/pub/OAuthApi.class")
        // Direct `ldc` of a dynamic constant (CONSTANT_Dynamic, JVM 11). 1.12.39 is the first
        // revision to emit these outside a makeConcatWithConstants recipe -- String[], boolean[]
        // and Properties[] lookup tables built by ConstantBootstraps.invoke(client.xx()) -- and
        // D8 refuses const-dynamic at every API level. Each distinct constant becomes a holder
        // class whose <clinit> runs the bootstrap once; the ldc becomes a GETSTATIC on it. That
        // keeps the JVM's evaluate-once semantics, which an inline call would not.
        val condys = CondyRegistry("runelite/desugar/Condy")

        // The holder classes sit in their own package, but a dynamic constant's bootstrap target
        // is typically a private static in the obfuscated class -- the JVM resolves a condy with
        // the declaring class's own lookup, so it never needed access. The holder's <clinit> does:
        // first pass finds every target so the second can widen it (and its class) to public.
        // Same treatment the lambda pass gives its implementation methods.
        JarFile(inFile).use { input ->
            val entries = input.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class") || skipExact.contains(entry.name)) continue
                val bytes = input.getInputStream(entry).use { it.readBytes() }
                ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitLdcInsn(value: Any) {
                                if (value is org.objectweb.asm.ConstantDynamic) {
                                    condys.noteTarget(value)
                                }
                            }
                        }
                    }
                }, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)
            }
        }

        JarFile(inFile).use { input ->
            JarOutputStream(outFile.outputStream().buffered()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    if (skipExact.contains(entry.name)) {
                        classesSkipped++
                        continue
                    }
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(JarEntry(entry.name))
                    if (entry.name.endsWith(".class")) {
                        classesProcessed++
                        val (rewritten, n) = rewriteClass(bytes, helperOwner.get(), condys)
                        sitesRewritten += n
                        output.write(rewritten)
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
                for ((name, classBytes) in condys.holders()) {
                    output.putNextEntry(JarEntry("$name.class"))
                    output.write(classBytes)
                    output.closeEntry()
                }
            }
        }
        logger.lifecycle("desugarStringConcat: processed $classesProcessed classes, rewrote $sitesRewritten makeConcatWithConstants sites, skipped $classesSkipped (runelite-api duplicates), hoisted ${condys.count()} dynamic constants into holder classes, widened ${condys.targetCount()} bootstrap targets")
    }

    private fun rewriteClass(bytes: ByteArray, helperOwner: String, condys: CondyRegistry): Pair<ByteArray, Int> {
        val cr = ClassReader(bytes)
        // The injected jar references internal obfuscated types like `da` that aren't on
        // ASM's reflection classloader, so the default getCommonSuperClass blows up.
        // Fall back to java/lang/Object — over-broad, but valid.
        val cw = object : ClassWriter(cr, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS) {
            override fun getCommonSuperClass(t1: String, t2: String): String {
                return try { super.getCommonSuperClass(t1, t2) } catch (_: Throwable) { "java/lang/Object" }
            }
        }
        val counter = intArrayOf(0)
        cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            var className = ""

            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                className = name
                val widened = if (condys.isTargetOwner(name)) (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or Opcodes.ACC_PUBLIC else access
                super.visit(version, widened, name, signature, superName, interfaces)
            }

            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val widened = if (name != null && descriptor != null && condys.isTarget(className, name, descriptor))
                    (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or Opcodes.ACC_PUBLIC
                else access
                val mv = super.visitMethod(widened, name, descriptor, signature, exceptions)
                return ConcatRewriter(widened, name, descriptor, mv, helperOwner, counter, condys)
            }
        }, ClassReader.EXPAND_FRAMES)
        return cw.toByteArray() to counter[0]
    }
}

// One holder class per distinct dynamic constant:
//
//     public final class runelite/desugar/Condy$N { public static final <desc> VALUE; static { ... } }
//
// <clinit> pushes the bootstrap's trailing arguments and invokes the handle, exactly what
// ConstantBootstraps.invoke does, then casts to the constant's own type. Only the
// ConstantBootstraps.invoke + H_INVOKESTATIC shape is supported; any other bootstrap fails the
// build with the constant named, since an unhandled one would fail D8 anyway.
private class CondyRegistry(private val prefix: String) {
    private val names = LinkedHashMap<org.objectweb.asm.ConstantDynamic, String>()

    // owner/name/desc of every bootstrap target seen in the scan pass; these get widened to public.
    private val targets = HashSet<Triple<String, String, String>>()
    private val targetOwners = HashSet<String>()

    fun noteTarget(c: org.objectweb.asm.ConstantDynamic) {
        val bsm = c.bootstrapMethod
        if (bsm.owner == "java/lang/invoke/ConstantBootstraps" && bsm.name == "invoke" && c.bootstrapMethodArgumentCount >= 1) {
            val h = c.getBootstrapMethodArgument(0) as? Handle ?: return
            targets.add(Triple(h.owner, h.name, h.desc))
            targetOwners.add(h.owner)
        }
    }

    fun isTarget(owner: String, name: String, desc: String): Boolean = targets.contains(Triple(owner, name, desc))
    fun isTargetOwner(owner: String): Boolean = targetOwners.contains(owner)
    fun targetCount(): Int = targets.size

    fun holderFor(c: org.objectweb.asm.ConstantDynamic): String =
        names.getOrPut(c) { "$prefix\$${names.size}" }

    fun count(): Int = names.size

    fun holders(): List<Pair<String, ByteArray>> = names.map { (c, name) -> name to generate(name, c) }

    private fun generate(name: String, c: org.objectweb.asm.ConstantDynamic): ByteArray {
        val bsm = c.bootstrapMethod
        val isInvoke = bsm.owner == "java/lang/invoke/ConstantBootstraps"
            && bsm.name == "invoke"
            && c.bootstrapMethodArgumentCount >= 1
        val implHandle = if (isInvoke) c.getBootstrapMethodArgument(0) as? Handle else null
        if (implHandle == null || implHandle.tag != Opcodes.H_INVOKESTATIC) {
            throw org.gradle.api.GradleException(
                "desugarStringConcat: unsupported dynamic constant bootstrap ${bsm.owner}.${bsm.name} for ${c.name}:${c.descriptor}"
            )
        }

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC, name, null, "java/lang/Object", null)
        cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "VALUE", c.descriptor, null, null).visitEnd()

        // The 5-arg constructor is protected; an anonymous subclass is how ConcatRewriter reaches it too.
        val clinit = object : GeneratorAdapter(Opcodes.ASM9, cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null), Opcodes.ACC_STATIC, "<clinit>", "()V") {}
        clinit.visitCode()

        val implArgTypes = Type.getArgumentTypes(implHandle.desc)
        for (i in implArgTypes.indices) {
            val argValue = if (i + 1 < c.bootstrapMethodArgumentCount) c.getBootstrapMethodArgument(i + 1) else null
            when (argValue) {
                is Int, is Long, is Float, is Double, is String, is Type, is Handle -> clinit.visitLdcInsn(argValue)
                null -> clinit.visitInsn(Opcodes.ACONST_NULL)
                else -> throw org.gradle.api.GradleException(
                    "desugarStringConcat: unsupported bootstrap argument ${argValue.javaClass.name} for ${c.name}:${c.descriptor}"
                )
            }
        }
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, implHandle.owner, implHandle.name, implHandle.desc, implHandle.isInterface)

        // Coerce the impl's return to the constant's declared type, the way the JVM does after
        // ConstantBootstraps.invoke returns Object.
        val retType = Type.getReturnType(implHandle.desc)
        val valueType = Type.getType(c.descriptor)
        val retIsRef = retType.sort == Type.OBJECT || retType.sort == Type.ARRAY
        val valueIsRef = valueType.sort == Type.OBJECT || valueType.sort == Type.ARRAY
        when {
            retIsRef && valueIsRef -> if (retType != valueType) clinit.checkCast(valueType)
            retIsRef && !valueIsRef -> clinit.unbox(valueType)
            !retIsRef && valueIsRef -> clinit.box(retType)
            else -> if (retType != valueType) clinit.cast(retType, valueType)
        }

        clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, "VALUE", c.descriptor)
        clinit.visitInsn(Opcodes.RETURN)
        clinit.visitMaxs(0, 0)
        clinit.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}

private class ConcatRewriter(
    access: Int,
    name: String?,
    private val descriptor: String?,
    delegate: MethodVisitor,
    private val helperOwner: String,
    private val counter: IntArray,
    private val condys: CondyRegistry,
) : GeneratorAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {

    override fun visitLdcInsn(value: Any) {
        if (value is org.objectweb.asm.ConstantDynamic) {
            val holder = condys.holderFor(value)
            mv.visitFieldInsn(Opcodes.GETSTATIC, holder, "VALUE", value.descriptor)
            return
        }
        super.visitLdcInsn(value)
    }

    // ART's verifier is stricter than the JVM's about narrow integer types. The 1.12.39
    // obfuscator emits opaque branches such as `ldc 2022116277; istore_1; ...; iload_1; ireturn`
    // inside a `byte` method: the JVM is happy to ireturn any int from a byte method, ART rejects
    // the class outright ("register v1 has type IntegerConstant but expected Byte"), and D8 does
    // not narrow constants for it. Putting the declared width back with i2b/i2s/i2c before every
    // narrow return, narrow field store and narrow array store is exactly what javac's own output
    // already looks like, so it changes nothing for real code and makes the junk verify.
    private val narrowReturn: Int = when (Type.getReturnType(descriptor ?: "()V").sort) {
        Type.BYTE -> Opcodes.I2B
        Type.SHORT -> Opcodes.I2S
        Type.CHAR -> Opcodes.I2C
        else -> 0
    }

    override fun visitInsn(opcode: Int) {
        when (opcode) {
            Opcodes.IRETURN -> if (narrowReturn != 0) super.visitInsn(narrowReturn)
            Opcodes.BASTORE -> super.visitInsn(Opcodes.I2B)
            Opcodes.SASTORE -> super.visitInsn(Opcodes.I2S)
            Opcodes.CASTORE -> super.visitInsn(Opcodes.I2C)
        }
        super.visitInsn(opcode)
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            when (desc) {
                "B" -> super.visitInsn(Opcodes.I2B)
                "S" -> super.visitInsn(Opcodes.I2S)
                "C" -> super.visitInsn(Opcodes.I2C)
            }
        }
        super.visitFieldInsn(opcode, owner, name, desc)
    }

    override fun visitInvokeDynamicInsn(name: String, descriptor: String, bsm: Handle, vararg bsmArgs: Any) {
        if (!(bsm.owner == "java/lang/invoke/StringConcatFactory" && bsm.name == "makeConcatWithConstants")) {
            super.visitInvokeDynamicInsn(name, descriptor, bsm, *bsmArgs)
            return
        }
        counter[0]++
        val argTypes = Type.getArgumentTypes(descriptor)
        // Save args off the stack into fresh locals (reverse order, since the top of stack is last arg).
        val locals = IntArray(argTypes.size)
        for (i in argTypes.size - 1 downTo 0) {
            val local = newLocal(argTypes[i])
            locals[i] = local
            storeLocal(local, argTypes[i])
        }
        // args[] = new Object[N]
        push(argTypes.size)
        newArray(Type.getObjectType("java/lang/Object"))
        for (i in argTypes.indices) {
            dup()
            push(i)
            loadLocal(locals[i], argTypes[i])
            box(argTypes[i])
            arrayStore(Type.getObjectType("java/lang/Object"))
        }
        val argsLocal = newLocal(Type.getObjectType("[Ljava/lang/Object;"))
        storeLocal(argsLocal, Type.getObjectType("[Ljava/lang/Object;"))

        // push recipe
        val recipe = bsmArgs[0] as String
        push(recipe)

        // push args[]
        loadLocal(argsLocal, Type.getObjectType("[Ljava/lang/Object;"))

        // consts[] = new Object[bsmArgs.length - 1]
        val constsLen = bsmArgs.size - 1
        push(constsLen)
        newArray(Type.getObjectType("java/lang/Object"))
        for (i in 0 until constsLen) {
            dup()
            push(i)
            pushConst(bsmArgs[i + 1])
            arrayStore(Type.getObjectType("java/lang/Object"))
        }

        // IndyConcat.concat(recipe, args, consts)
        invokeStatic(
            Type.getObjectType(helperOwner),
            org.objectweb.asm.commons.Method(
                "concat",
                "(Ljava/lang/String;[Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/String;"
            )
        )
    }

    private fun resolveCondy(c: org.objectweb.asm.ConstantDynamic) {
        val bsm = c.bootstrapMethod
        val isInvoke = bsm.owner == "java/lang/invoke/ConstantBootstraps"
            && bsm.name == "invoke"
            && c.bootstrapMethodArgumentCount >= 1
        val implHandle = if (isInvoke) c.getBootstrapMethodArgument(0) as? Handle else null
        if (implHandle == null || implHandle.tag != Opcodes.H_INVOKESTATIC) {
            // Unknown CONDY shape — fall back to a safe placeholder. Strip NULs so the
            // result can't trip xi.cq's NUL-rejection downstream.
            push(c.toString().replace(' ', '_'))
            return
        }
        // Push the trailing bsm args (positions 1..N) as primitive constants matching
        // the impl method's argument types.
        val implArgTypes = Type.getArgumentTypes(implHandle.desc)
        for (i in implArgTypes.indices) {
            val argValue = if (i + 1 < c.bootstrapMethodArgumentCount) c.getBootstrapMethodArgument(i + 1) else null
            when (argValue) {
                is Int -> mv.visitLdcInsn(argValue)
                is Long -> mv.visitLdcInsn(argValue)
                is Float -> mv.visitLdcInsn(argValue)
                is Double -> mv.visitLdcInsn(argValue)
                is String -> mv.visitLdcInsn(argValue)
                is Type -> mv.visitLdcInsn(argValue)
                is Handle -> mv.visitLdcInsn(argValue)
                null -> mv.visitInsn(Opcodes.ACONST_NULL)
                else -> mv.visitLdcInsn(argValue.toString())
            }
        }
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            implHandle.owner,
            implHandle.name,
            implHandle.desc,
            implHandle.isInterface,
        )
        // The static call leaves the impl's return value on the stack. For the consts[]
        // array store we need an Object reference — box primitives if needed.
        val retType = Type.getReturnType(implHandle.desc)
        if (retType.sort != Type.OBJECT && retType.sort != Type.ARRAY && retType.sort != Type.VOID) {
            box(retType)
        } else if (retType.sort == Type.VOID) {
            // Shouldn't happen for a constant resolver, but keep the stack sane.
            mv.visitInsn(Opcodes.ACONST_NULL)
        }
    }

    private fun pushConst(c: Any?) {
        when (c) {
            is String -> push(c)
            is Int -> { push(c); box(Type.INT_TYPE) }
            is Long -> { push(c); box(Type.LONG_TYPE) }
            is Float -> { push(c); box(Type.FLOAT_TYPE) }
            is Double -> { push(c); box(Type.DOUBLE_TYPE) }
            is Type -> push(c)            // class literal — LDC Class
            is Handle -> push(c)          // method handle  — LDC MethodHandle
            // ConstantDynamic (Java 11+ CONDY). The patched OSRS client uses these as
            // makeConcatWithConstants bootstrap constants — the JVM resolves them at
            // first use by invoking the bootstrap (typically ConstantBootstraps.invoke
            // calling some `client.kt(J)String` lazy initializer). D8 rejects CONDY in
            // dex output (UnsupportedFeature(const-dynamic)) even at minSdk=26, so we
            // can't just emit LDC ConstantDynamic. Without this resolution path the
            // default `push(c.toString())` branch would run, baking ASM's debug repr
            // (which contains a NUL char from the CONDY's name) into a string literal;
            // that NUL then leaks through to xi.cq during login machine-info
            // serialization and the server replies "RuneScape has been updated".
            //
            // Resolution strategy: when the CONDY's bootstrap is
            // ConstantBootstraps.invoke + an H_INVOKESTATIC handle (the only shape we've
            // observed), emit the equivalent direct invocation inline. The JVM will run
            // it every time the consts[] array is built; for makeConcatWithConstants
            // that's once per call site which is the same as desktop's CONDY caching
            // semantics for purposes of the produced string.
            is org.objectweb.asm.ConstantDynamic -> resolveCondy(c)
            null -> visitInsn(Opcodes.ACONST_NULL)
            else -> push(c.toString())    // give up — best effort literal
        }
    }
}

// -- DesugarLambdasTask -----------------------------------------------------------------
// One pass over the input jar; every invokedynamic targeting LambdaMetafactory.metafactory
// becomes a `new SyntheticLambdaN(...)` + invokespecial, where SyntheticLambdaN is a
// generated class implementing the functional interface and delegating its SAM call to
// the original implMethod with the captured args prepended.
//
// Only handles the standard `metafactory` (3 BSM args). `altMetafactory` would need the
// flag-bits decoded; we haven't seen it in the patched client.
abstract class DesugarLambdasTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputJar: org.gradle.api.file.RegularFileProperty
    @get:org.gradle.api.tasks.OutputFile
    abstract val outputJar: org.gradle.api.file.RegularFileProperty
    @get:org.gradle.api.tasks.Input
    abstract val syntheticPrefix: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val inFile = inputJar.get().asFile
        val outFile = outputJar.get().asFile
        outFile.parentFile.mkdirs()

        // First pass: collect every implMethod we'll need to invoke from synthetic classes
        // so we can widen its access if it's private (cross-class private calls fail at
        // verify time). Also identify methods matching the xi.cq IAE-on-NUL pattern so
        // we can prepend a StringShim.stripNul prologue and stop login from crashing
        // with "error_game_crash" when a machine-info string picks up a NUL.
        val privatesToWiden = mutableSetOf<Triple<String, String, String>>()
        val nulRejectMethods = mutableSetOf<Triple<String, String, String>>()
        JarFile(inFile).use { input ->
            val entries = input.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class")) continue
                val bytes = input.getInputStream(entry).use { it.readBytes() }
                var currentOwner = ""
                ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, ifaces: Array<out String>?) {
                        currentOwner = name
                    }
                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                        val methodOwner = currentOwner
                        val methodName = name
                        val methodDesc = descriptor
                        val isStringIntVoid = descriptor == "(Ljava/lang/String;I)V"
                        return object : MethodVisitor(Opcodes.ASM9) {
                            var sawIndexOfChar = false
                            var sawIaeNew = false
                            override fun visitInvokeDynamicInsn(n: String, d: String, bsm: Handle, vararg args: Any) {
                                if (bsm.owner == "java/lang/invoke/LambdaMetafactory" && bsm.name == "metafactory") {
                                    val impl = args[1] as Handle
                                    privatesToWiden.add(Triple(impl.owner, impl.name, impl.desc))
                                }
                            }
                            override fun visitMethodInsn(opc: Int, owner: String, n: String, d: String, itf: Boolean) {
                                if (isStringIntVoid && owner == "java/lang/String" && n == "indexOf" && d == "(I)I") {
                                    sawIndexOfChar = true
                                }
                            }
                            override fun visitTypeInsn(opc: Int, type: String) {
                                if (isStringIntVoid && opc == Opcodes.NEW && type == "java/lang/IllegalArgumentException") {
                                    sawIaeNew = true
                                }
                            }
                            override fun visitEnd() {
                                if (sawIndexOfChar && sawIaeNew && methodName != null && methodDesc != null) {
                                    nulRejectMethods.add(Triple(methodOwner, methodName, methodDesc))
                                }
                            }
                        }
                    }
                }, ClassReader.SKIP_FRAMES)
            }
        }

        var lambdaCount = 0
        val synthetics = mutableListOf<Pair<String, ByteArray>>()

        JarFile(inFile).use { input ->
            JarOutputStream(outFile.outputStream().buffered()).use { output ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(JarEntry(entry.name))
                    if (entry.name.endsWith(".class")) {
                        val (rewritten, newSynthetics) = rewriteClass(bytes, privatesToWiden, nulRejectMethods, lambdaCount)
                        lambdaCount += newSynthetics.size
                        synthetics.addAll(newSynthetics)
                        output.write(rewritten)
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
                for ((name, classBytes) in synthetics) {
                    output.putNextEntry(JarEntry("$name.class"))
                    output.write(classBytes)
                    output.closeEntry()
                }
            }
        }
        logger.lifecycle("desugarLambdas: produced ${synthetics.size} synthetic lambda classes; widened ${privatesToWiden.size} potential impl methods; patched ${nulRejectMethods.size} NUL-rejecting string writers")
        nulRejectMethods.forEach { (o, n, d) -> logger.lifecycle("  nulPatched: $o.$n$d") }
    }

    private fun rewriteClass(bytes: ByteArray, privatesToWiden: Set<Triple<String, String, String>>, nulRejectMethods: Set<Triple<String, String, String>>, startCounter: Int): Pair<ByteArray, List<Pair<String, ByteArray>>> {
        val cr = ClassReader(bytes)
        val cw = object : ClassWriter(cr, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS) {
            override fun getCommonSuperClass(t1: String, t2: String): String {
                return try { super.getCommonSuperClass(t1, t2) } catch (_: Throwable) { "java/lang/Object" }
            }
        }
        val syntheticsForThisClass = mutableListOf<Pair<String, ByteArray>>()
        val prefix = syntheticPrefix.get()
        var counter = startCounter
        var ownerName = ""

        cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, ifaces: Array<out String>?) {
                ownerName = name
                super.visit(version, access, name, sig, superName, ifaces)
            }

            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                // Widen impl methods to PUBLIC so synthetic lambdas can call them. The
                // synthetic classes live in the runelite/desugar package — a DIFFERENT
                // package from the impl's owner (default package / obfuscated names) — so
                // merely stripping ACC_PRIVATE leaves them package-private, which still
                // fails cross-package access with IllegalAccessError at link time. Forcing
                // public is always verification-safe (it only widens access).
                val effAccess = if (privatesToWiden.any { it.first == ownerName && it.second == name && it.third == descriptor }) {
                    (access and Opcodes.ACC_PRIVATE.inv() and Opcodes.ACC_PROTECTED.inv()) or Opcodes.ACC_PUBLIC
                } else access
                val mv = super.visitMethod(effAccess, name, descriptor, signature, exceptions)
                val rewriter = LambdaRewriter(effAccess, name, descriptor, mv, prefix, ownerName) { sig, implHandle, captureDesc ->
                    val syntheticName = "$prefix$counter"
                    counter++
                    syntheticsForThisClass.add(syntheticName to generateSyntheticLambda(syntheticName, sig, implHandle, captureDesc))
                    syntheticName
                }
                // If this method's body matched the IAE-on-NUL pattern in the first pass,
                // inject `arg1 = StringShim.stripNul(arg1)` at method entry. The method
                // never sees a NUL, so the IAE never throws.
                val shouldPatchNul = nulRejectMethods.contains(Triple(ownerName, name, descriptor))
                return if (shouldPatchNul) {
                    object : MethodVisitor(Opcodes.ASM9, rewriter) {
                        override fun visitCode() {
                            super.visitCode()
                            super.visitVarInsn(Opcodes.ALOAD, 1)
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "net/runelite/mp/util/StringShim",
                                "stripNul",
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                false,
                            )
                            super.visitVarInsn(Opcodes.ASTORE, 1)
                        }
                    }
                } else {
                    rewriter
                }
            }
        }, ClassReader.EXPAND_FRAMES)

        return cw.toByteArray() to syntheticsForThisClass
    }

    private fun generateSyntheticLambda(
        synName: String,
        spec: LambdaSpec,
        impl: Handle,
        captureDesc: String,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        val fiName = Type.getReturnType(spec.invokedDescriptor).internalName
        val captureTypes = Type.getArgumentTypes(captureDesc)

        cw.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
            synName,
            null,
            "java/lang/Object",
            arrayOf(fiName),
        )

        // Capture fields.
        captureTypes.forEachIndexed { i, t ->
            cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC, "arg$$i", t.descriptor, null, null).visitEnd()
        }

        // Constructor: store captures into fields.
        run {
            val ctorDesc = "(${captureTypes.joinToString("") { it.descriptor }})V"
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc, null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            var slot = 1
            captureTypes.forEachIndexed { i, t ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot)
                mv.visitFieldInsn(Opcodes.PUTFIELD, synName, "arg$$i", t.descriptor)
                slot += t.size
            }
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }

        // SAM method body.
        run {
            val mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                spec.samName,
                spec.samMethodType.descriptor,
                null,
                null,
            )
            mv.visitCode()

            val samArgs = Type.getArgumentTypes(spec.samMethodType.descriptor)
            val instantiatedArgs = Type.getArgumentTypes(spec.instantiatedMethodType.descriptor)
            val implArgs = Type.getArgumentTypes(impl.desc)

            // For NEWINVOKESPECIAL the impl is a constructor: emit NEW + DUP first.
            if (impl.tag == Opcodes.H_NEWINVOKESPECIAL) {
                mv.visitTypeInsn(Opcodes.NEW, impl.owner)
                mv.visitInsn(Opcodes.DUP)
            }

            // Push captures.
            captureTypes.forEachIndexed { i, t ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitFieldInsn(Opcodes.GETFIELD, synName, "arg$$i", t.descriptor)
            }

            // For INVOKESPECIAL / INVOKEVIRTUAL / INVOKEINTERFACE (non-static method
            // references), the first "captured" arg is the receiver. We treat it as part of
            // captures already above.

            // Push SAM args, adapting types to impl's expected arg types.
            var slot = 1
            samArgs.forEachIndexed { i, samArg ->
                mv.visitVarInsn(samArg.getOpcode(Opcodes.ILOAD), slot)
                slot += samArg.size

                val targetIdx = captureTypes.size + i
                val instArg = if (targetIdx < instantiatedArgs.size + captureTypes.size) instantiatedArgs.getOrNull(i) else null
                val implArgTarget = when (impl.tag) {
                    Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKEINTERFACE, Opcodes.H_INVOKESPECIAL -> {
                        // implArgs is the static descriptor without the receiver, but Handle.desc here is the method's actual descriptor without receiver. We rely on Type adaptation between sam and inst.
                        instArg
                    }
                    else -> implArgs.getOrNull(captureTypes.size + i - (if (impl.tag == Opcodes.H_NEWINVOKESPECIAL) 0 else 0))
                }
                if (instArg != null && samArg != instArg) {
                    adaptValue(mv, samArg, instArg)
                }
            }

            // Invoke the impl method.
            val invokeOpcode = when (impl.tag) {
                Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC
                Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL
                Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE
                // A reference to a PRIVATE instance method compiles to H_INVOKESPECIAL.
                // The synthetic lambda class is NOT a subclass of impl.owner, so emitting a
                // literal INVOKESPECIAL is a VerifyError (invokespecial is only legal for
                // the current class or a superclass — on Android this throws at class-load
                // and, because the gamepack wraps rendering in try/catch, silently drops
                // whatever that lambda was driving). We've widened the target to public, so
                // dispatch it virtually instead — the standard lambda-desugaring lowering.
                // NEWINVOKESPECIAL (constructor refs) genuinely needs INVOKESPECIAL.
                Opcodes.H_INVOKESPECIAL -> if (impl.isInterface) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
                Opcodes.H_NEWINVOKESPECIAL -> Opcodes.INVOKESPECIAL
                Opcodes.H_GETFIELD -> Opcodes.GETFIELD
                Opcodes.H_GETSTATIC -> Opcodes.GETSTATIC
                Opcodes.H_PUTFIELD -> Opcodes.PUTFIELD
                Opcodes.H_PUTSTATIC -> Opcodes.PUTSTATIC
                else -> Opcodes.INVOKESTATIC
            }
            val implName = if (impl.tag == Opcodes.H_NEWINVOKESPECIAL) "<init>" else impl.name
            val implDesc = if (impl.tag == Opcodes.H_NEWINVOKESPECIAL) {
                // The impl handle's descriptor for ctor refs already takes the constructor's args
                // and returns the constructed type, but JVM expects ctor descs to return V.
                val t = Type.getMethodType(impl.desc)
                Type.getMethodDescriptor(Type.VOID_TYPE, *t.argumentTypes)
            } else {
                impl.desc
            }
            mv.visitMethodInsn(invokeOpcode, impl.owner, implName, implDesc, impl.isInterface)

            // Adapt return value to SAM return type.
            val samReturn = Type.getReturnType(spec.samMethodType.descriptor)
            val implReturn = if (impl.tag == Opcodes.H_NEWINVOKESPECIAL) Type.getObjectType(impl.owner) else Type.getReturnType(impl.desc)
            if (samReturn != implReturn) {
                if (samReturn.sort == Type.VOID) {
                    if (implReturn.size > 0) mv.visitInsn(if (implReturn.size == 2) Opcodes.POP2 else Opcodes.POP)
                } else {
                    adaptValue(mv, implReturn, samReturn)
                }
            }
            mv.visitInsn(samReturn.getOpcode(Opcodes.IRETURN))
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }

        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun adaptValue(mv: MethodVisitor, from: Type, to: Type) {
        if (from == to) return
        if (from.sort == Type.OBJECT || from.sort == Type.ARRAY) {
            if (to.sort == Type.OBJECT || to.sort == Type.ARRAY) {
                if (to.internalName != "java/lang/Object") {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, to.internalName)
                }
            }
        }
    }
}

data class LambdaSpec(
    val invokedDescriptor: String,
    val samName: String,
    val samMethodType: Type,
    val instantiatedMethodType: Type,
)

private class LambdaRewriter(
    access: Int,
    name: String?,
    descriptor: String?,
    delegate: MethodVisitor,
    private val prefix: String,
    private val ownerName: String,
    private val genSynthetic: (LambdaSpec, Handle, String) -> String,
) : GeneratorAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {

    override fun visitInvokeDynamicInsn(name: String, descriptor: String, bsm: Handle, vararg bsmArgs: Any) {
        if (!(bsm.owner == "java/lang/invoke/LambdaMetafactory" && bsm.name == "metafactory")) {
            super.visitInvokeDynamicInsn(name, descriptor, bsm, *bsmArgs)
            return
        }
        val samMethodType = bsmArgs[0] as Type
        val implMethod = bsmArgs[1] as Handle
        val instantiatedMethodType = bsmArgs[2] as Type
        val spec = LambdaSpec(descriptor, name, samMethodType, instantiatedMethodType)
        val captureDesc = Type.getMethodDescriptor(Type.VOID_TYPE, *Type.getArgumentTypes(descriptor))
        val synName = genSynthetic(spec, implMethod, captureDesc)

        // Save captures to locals (in reverse order so we can reload in original order).
        val captureTypes = Type.getArgumentTypes(descriptor)
        val captureLocals = IntArray(captureTypes.size)
        for (i in captureTypes.indices.reversed()) {
            captureLocals[i] = newLocal(captureTypes[i])
            storeLocal(captureLocals[i], captureTypes[i])
        }

        // NEW + DUP, then reload captures, then INVOKESPECIAL <init>.
        visitTypeInsn(Opcodes.NEW, synName)
        visitInsn(Opcodes.DUP)
        captureTypes.forEachIndexed { i, t -> loadLocal(captureLocals[i], t) }
        val ctorDesc = "(${captureTypes.joinToString("") { it.descriptor }})V"
        visitMethodInsn(Opcodes.INVOKESPECIAL, synName, "<init>", ctorDesc, false)
    }

    // Android's sun.misc.Unsafe is missing the public static constant fields like
    // ARRAY_BYTE_BASE_OFFSET, ARRAY_*_INDEX_SCALE, ADDRESS_SIZE. Rewrite GETSTATIC on
    // those into a read of net/runelite/mp/util/UnsafeShim, which seeds them from a
    // live Unsafe instance at <clinit>.
    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        if (opcode == Opcodes.GETSTATIC && owner == "sun/misc/Unsafe" && UNSAFE_CONSTS.contains(name)) {
            super.visitFieldInsn(Opcodes.GETSTATIC, "net/runelite/mp/util/UnsafeShim", name, descriptor)
            return
        }
        super.visitFieldInsn(opcode, owner, name, descriptor)
    }

    // Reroute every off-heap-style INVOKEVIRTUAL on sun.misc.Unsafe into an INVOKESTATIC
    // on net/runelite/mp/util/UnsafeShim with the same descriptor. The shim emulates
    // off-heap memory in a userland Map<Integer, ByteBuffer> so we never depend on the
    // (Android-restricted) hidden allocateMemory / copyMemoryFromPrimitiveArray APIs.
    //
    // Also reroute ProcessHandle.Info.command() through ProcessHandleShim, which strips
    // the trailing NUL-separated argv tail Android's /proc/PID/cmdline returns. The
    // patched client serializes that string into its login machine-info packet (xi.cq),
    // which rejects any string containing NUL — without this redirect, login crashes
    // with "error_game_crash".
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (opcode == Opcodes.INVOKEVIRTUAL && owner == "sun/misc/Unsafe"
            && UNSAFE_REDIRECT.contains(name + descriptor)) {
            // Stack on entry: [Unsafe receiver, arg0, arg1, …]
            // We need to pop the receiver but it's underneath every arg. Save args into
            // locals (reverse order so reloads come out in argument order), POP the
            // receiver, reload args, and call the static.
            val argTypes = Type.getArgumentTypes(descriptor)
            val locals = IntArray(argTypes.size)
            for (i in argTypes.indices.reversed()) {
                locals[i] = newLocal(argTypes[i])
                storeLocal(locals[i], argTypes[i])
            }
            super.visitInsn(Opcodes.POP)
            for (i in argTypes.indices) {
                loadLocal(locals[i], argTypes[i])
            }
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/runelite/mp/util/UnsafeShim",
                name, descriptor, false)
            return
        }
        if (opcode == Opcodes.INVOKEINTERFACE
            && owner == "java/lang/ProcessHandle\$Info"
            && name == "command"
            && descriptor == "()Ljava/util/Optional;") {
            // Stack: [ProcessHandle$Info receiver]. The shim's parameter type is Object
            // (Android's android.jar hides ProcessHandle from javac), so we just upcast
            // the receiver to Object on the stack — same single-arg static call.
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "net/runelite/mp/util/ProcessHandleShim",
                "sanitizedCommand",
                "(Ljava/lang/Object;)Ljava/util/Optional;",
                false,
            )
            return
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    companion object {
        private val UNSAFE_CONSTS: Set<String> = setOf(
            "ADDRESS_SIZE", "INVALID_FIELD_OFFSET",
            "ARRAY_BOOLEAN_BASE_OFFSET", "ARRAY_BYTE_BASE_OFFSET", "ARRAY_SHORT_BASE_OFFSET",
            "ARRAY_CHAR_BASE_OFFSET", "ARRAY_INT_BASE_OFFSET", "ARRAY_LONG_BASE_OFFSET",
            "ARRAY_FLOAT_BASE_OFFSET", "ARRAY_DOUBLE_BASE_OFFSET", "ARRAY_OBJECT_BASE_OFFSET",
            "ARRAY_BOOLEAN_INDEX_SCALE", "ARRAY_BYTE_INDEX_SCALE", "ARRAY_SHORT_INDEX_SCALE",
            "ARRAY_CHAR_INDEX_SCALE", "ARRAY_INT_INDEX_SCALE", "ARRAY_LONG_INDEX_SCALE",
            "ARRAY_FLOAT_INDEX_SCALE", "ARRAY_DOUBLE_INDEX_SCALE", "ARRAY_OBJECT_INDEX_SCALE",
        )

        // (name + descriptor) pairs we redirect from Unsafe to UnsafeShim. Off-heap-only
        // variants are listed; the heap-array INVOKEVIRTUAL forms (putByte(Object,J,B) etc)
        // go through Android's native Unsafe untouched since those aren't restricted.
        private val UNSAFE_REDIRECT: Set<String> = setOf(
            "allocateMemory" + "(J)J",
            "reallocateMemory" + "(JJ)J",
            "freeMemory" + "(J)V",
            "setMemory" + "(JJB)V",
            "setMemory" + "(Ljava/lang/Object;JJB)V",
            "copyMemory" + "(JJJ)V",
            "copyMemory" + "(Ljava/lang/Object;JLjava/lang/Object;JJ)V",
            "getByte" + "(J)B",   "putByte"   + "(JB)V",
            "getShort" + "(J)S",  "putShort"  + "(JS)V",
            "getChar" + "(J)C",   "putChar"   + "(JC)V",
            "getInt" + "(J)I",    "putInt"    + "(JI)V",
            "getLong" + "(J)J",   "putLong"   + "(JJ)V",
            "getFloat" + "(J)F",  "putFloat"  + "(JF)V",
            "getDouble" + "(J)D", "putDouble" + "(JD)V",
        )
    }
}
