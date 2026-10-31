import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Properties

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
    // runelite-client's runtimeOnly("net.runelite:injected-client:…") is JVM bytecode AGP's D8
    // can't dex as-is; ClientPreparer fetches the same artifact at runtime and desugars it.
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

// Release version. CI tags releases as <major>.<minor>.<patch>_<RuneLite version>. The game
// client isn't bundled: ClientPreparer downloads the injected client matching the bundled
// runelite-client from repo.runelite.net and desugars + dexes it on the device.
val versionMajor = 1
val versionMinor = 0
val versionPatch = 59
val releaseTag = "$versionMajor.$versionMinor.${versionPatch}_${project.version}"
val releaseVersionCode = (versionMajor * 1_000_000) + (versionMinor * 1_000) + versionPatch

// CI reads the same values used by the APK and the in-app updater.
tasks.register("writeReleaseVersion") {
    val destination = layout.buildDirectory.file("release-version.json")
    inputs.property("tag", releaseTag)
    inputs.property("versionCode", releaseVersionCode)
    outputs.file(destination)
    doLast {
        destination.get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.toJson(mapOf("tag" to releaseTag, "versionCode" to releaseVersionCode)))
        }
    }
}

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
    // Polywoof's thin hub jar expects H2 in its parent loader. Keep the SQL engine
    // and resources, replacing the data source and excluding desktop integrations.
    val h2Runtime = configurations.create("h2Runtime") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(h2Runtime.name, "com.h2database:h2:2.1.212")
    val androidH2 = tasks.register<Jar>("androidH2") {
        archiveFileName.set("h2-android.jar")
        destinationDirectory.set(layout.buildDirectory.dir("android-h2"))
        from(provider { h2Runtime.files.map { zipTree(it) } })
        // Desktop integrations are not part of the embedded Android database runtime.
        val unsupported = listOf(
            "org/h2/jdbcx/**",
            "org/h2/server/web/*Servlet.class", "org/h2/server/web/*DbStarter.class",
            "org/h2/tools/GUIConsole*.class", "org/h2/fulltext/FullTextLucene*.class",
            "org/h2/util/DbDriverActivator.class", "org/h2/util/OsgiDataSourceFactory.class",
            "org/h2/util/AbbaLockingDetector*.class"
        )
        inputs.property("unsupportedIntegrations", unsupported)
        exclude(unsupported)
    }
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
        // Phone → watch relay of the Jagex sign-in redirect (WearRelay).
        "implementation"(libs.play.services.wearable)
        // android-awt provides the shadow java.awt / javax.swing / javax.sound surface
        // the runelite jars compile against; it must be on the classpath before
        // anything that touches AWT can resolve at dex time.
        "implementation"(project(":android-awt"))
        "implementation"(project(":android-hd"))
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
        // D8, run in-process by PluginDexer to convert upstream hub jars (JVM bytecode) to
        // dex on the device. Pure Java; kept whole in proguard-rules.pro.
        "implementation"("com.android.tools:r8:9.5.28")
        "implementation"(files(androidH2.flatMap { it.archiveFile }))
        // ASM for ClientPreparer's transforms over the downloaded injected client.
        "implementation"("org.ow2.asm:asm:9.8")
        "implementation"("org.ow2.asm:asm-commons:9.8")
        // Fallback for ClientPreparer's BaseDexClassLoader.addDexPath call (hidden API).
        "implementation"("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

        "implementation"("com.google.guava:guava:33.6.0-android")
    }
}

if (androidSdkAvailable) {
    tasks.register<Exec>("verifyReleasePluginApi") {
        group = "verification"
        description = "Verify external plugin library APIs survive release shrinking"
        dependsOn("assembleRelease")
        doFirst {
            val apk = layout.buildDirectory.dir("outputs/apk/release").get().asFile
                .listFiles { file -> file.extension == "apk" }?.singleOrNull()
                ?: error("Expected exactly one release APK")
            val libraries = configurations.getByName("releaseRuntimeClasspath").incoming.artifactView {
                attributes.attribute(org.gradle.api.attributes.Attribute.of("artifactType", String::class.java), "jar")
                componentFilter { id -> id is org.gradle.api.artifacts.component.ModuleComponentIdentifier &&
                    (id.module == "jopt-simple" || id.module == "protobuf-javalite") }
            }.files.files
                .filter { it.extension == "jar" && (it.name.startsWith("jopt-simple-") || it.name.startsWith("protobuf-javalite-")) }
            val python = providers.environmentVariable("PYTHON").orNull
                ?: if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
            commandLine(listOf(python, rootProject.file("tools-src/verify-release-plugin-api.py").absolutePath,
                "--apk", apk.absolutePath) + libraries.flatMap { listOf("--library-jar", it.absolutePath) })
        }
    }
}

// One version controls both the APK compiler and the on-device plugin/client compiler.
val coreLibraryVersion = "2.1.4"
if (androidSdkAvailable) {
    val runtimeDesugarConfiguration = configurations.create("runtimeDesugarConfiguration") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(runtimeDesugarConfiguration.name,
        "com.android.tools:desugar_jdk_libs_configuration:$coreLibraryVersion")
    val bootApi = provider {
        extensions.getByType<com.android.build.api.variant.ApplicationAndroidComponentsExtension>()
            .sdkComponents.bootClasspath.get()
    }
    val hostApi = provider {
        project(":android-awt").tasks.named<JavaCompile>("compileReleaseJavaWithJavac")
            .get().destinationDirectory.get().asFile
    }
    val generatedAssets = layout.buildDirectory.dir("generated/runtime-desugar-assets")
    val prepareRuntimeDesugarAssets = tasks.register("prepareRuntimeDesugarAssets") {
        dependsOn(":android-awt:compileReleaseJavaWithJavac")
        inputs.files(runtimeDesugarConfiguration, bootApi)
        inputs.dir(hostApi)
        inputs.property("coreLibraryVersion", coreLibraryVersion)
        outputs.dir(generatedAssets)
        doLast {
            val out = generatedAssets.get().dir("runtime-dex").asFile.apply { mkdirs() }
            val configuration = runtimeDesugarConfiguration.singleFile
            java.util.zip.ZipFile(configuration).use { archive ->
                archive.getInputStream(archive.getEntry("META-INF/desugar/d8/desugar.json")).use {
                    out.resolve("desugar.json").writeBytes(it.readBytes())
                }
            }
            // These are compiler API inputs, never loaded as application code. The
            // platform JDK surface and host compatibility hierarchy are enough for D8
            // to resolve core-library interfaces without shipping the whole SDK jar.
            java.util.zip.ZipOutputStream(out.resolve("java-api.jar").outputStream()).use { target ->
                val seen = mutableSetOf<String>()
                for (library in bootApi.get()) java.util.zip.ZipFile(library.asFile).use { archive ->
                    archive.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") &&
                        (it.name.startsWith("java/") || it.name.startsWith("javax/")) }
                        .sortedBy { it.name }.forEach { entry ->
                            if (seen.add(entry.name)) {
                                target.putNextEntry(java.util.zip.ZipEntry(entry.name).apply { time = 0 })
                                archive.getInputStream(entry).use { it.copyTo(target) }
                                target.closeEntry()
                            }
                        }
                }
            }
            java.util.zip.ZipOutputStream(out.resolve("host-api.jar").outputStream()).use { target ->
                hostApi.get().walkTopDown().filter { it.isFile && it.extension == "class" }
                    .sortedBy { it.relativeTo(hostApi.get()).invariantSeparatorsPath }.forEach { file ->
                        target.putNextEntry(java.util.zip.ZipEntry(file.relativeTo(hostApi.get()).invariantSeparatorsPath).apply { time = 0 })
                        file.inputStream().use { it.copyTo(target) }
                        target.closeEntry()
                    }
            }
            fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            out.resolve("inputs.properties").writeText(buildString {
                append("version=$coreLibraryVersion\n")
                append("configurationArtifact=${configuration.name}\n")
                append("configurationSha256=${sha256(configuration)}\n")
                for (name in listOf("desugar.json", "java-api.jar", "host-api.jar")) {
                    append("sha256.$name=${sha256(out.resolve(name))}\n")
                }
            })
        }
    }
    extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
        sourceSets.named("main") { assets.srcDir(generatedAssets.get().asFile) }
    }
    tasks.configureEach {
        if ((name.startsWith("merge") && name.endsWith("Assets")) ||
            name.contains("lint", ignoreCase = true)) {
            dependsOn(prepareRuntimeDesugarAssets)
        }
    }
}

// AGP's `coreLibraryDesugaring` configuration is set up by the android plugin after the
// KMP `kotlin {}` block runs, so add the polyfill dep here outside the source-set
// closures. desugar_jdk_libs 2.1.x is the version that ships the LambdaMetafactory
// polyfill alongside the rest of the modern JDK surface.
if (androidSdkAvailable) {
    // Downloaded clients/plugins are compiled after the APK's reachability analysis.
    // Preserve the desugared library ABI at L8 too, not only in the host R8 rules.
    tasks.withType<com.android.build.gradle.internal.tasks.L8DexDesugarLibTask>().configureEach {
        keepRulesConfigurations.add("-keep class j$.** { *; }")
    }
    dependencies {
        add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:$coreLibraryVersion")
        // Lombok — 117HD's java sources use @Slf4j / @Getter / @AllArgsConstructor.
        // Wired here because KMP source-set closures don't expose `annotationProcessor`;
        // AGP creates the config for the android variant.
        add("compileOnly", "org.projectlombok:lombok:1.18.30")
        add("annotationProcessor", "org.projectlombok:lombok:1.18.30")
    }
}

// shaderc (GLSL -> SPIR-V) for the 117 HD Vulkan backend, which compiles 117 HD's generated
// shaders at runtime. Built once per ABI from the NDK's own shaderc sources into
// android/.shaderc; CMake links the static libraries from there. The combined-archive target
// in the NDK's Android.mk breaks on Windows (its ar script quoting), so the component
// libraries are built and linked individually instead.
if (androidSdkAvailable) {
    val shadercAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    val shadercModules = listOf("shaderc", "shaderc_util", "glslang", "OSDependent", "OGLCompiler",
        "HLSL", "SPIRV", "SPIRV-Tools", "SPIRV-Tools-opt")
    val shadercOut = layout.projectDirectory.dir(".shaderc")
    val buildShaderc = tasks.register("buildShaderc") {
        group = "build"
        description = "Builds shaderc's static libraries from the NDK sources for every packaged ABI"
        val sdkDir = (System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
            ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
            ?: rootProject.file("local.properties").let { f ->
                Properties().apply { f.inputStream().use { load(it) } }.getProperty("sdk.dir")
            })
        val ndkDir = file("$sdkDir/ndk/27.1.12297006")
        outputs.dir(shadercOut)
        doLast {
            val missing = shadercAbis.filter { abi ->
                shadercModules.any { !shadercOut.file("obj/local/$abi/lib$it.a").asFile.isFile }
            }
            if (missing.isEmpty()) return@doLast
            val ndkBuild = File(ndkDir, if (System.getProperty("os.name").startsWith("Windows")) "ndk-build.cmd" else "ndk-build")
            val out = shadercOut.asFile
            val cmd = listOf(ndkBuild.absolutePath,
                "NDK_PROJECT_PATH=.", "APP_BUILD_SCRIPT=Android.mk", "APP_STL=c++_static",
                "APP_ABI=${missing.joinToString(" ")}", "APP_PLATFORM=android-26",
                "NDK_LIBS_OUT=${File(out, "libs").absolutePath.replace('\\', '/')}",
                "NDK_OUT=${File(out, "obj").absolutePath.replace('\\', '/')}",
                "-j${Runtime.getRuntime().availableProcessors()}") + shadercModules
            logger.lifecycle("Building shaderc for $missing (one-time, several minutes)")
            val proc = ProcessBuilder(cmd)
                .directory(File(ndkDir, "sources/third_party/shaderc"))
                .redirectErrorStream(true)
                .redirectOutput(File(out.apply { mkdirs() }, "build.log"))
                .start()
            if (proc.waitFor() != 0) throw GradleException("shaderc build failed; see ${File(out, "build.log")}")
        }
    }
    tasks.matching { it.name.startsWith("configureCMake") }.configureEach { dependsOn(buildShaderc) }
}

if (androidSdkAvailable) {
    // AGP 9's public DSL type; BaseAppModuleExtension was the pre-newDsl internal one.
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        ndkVersion = "27.1.12297006"
        externalNativeBuild { cmake { path = file("src/androidMain/cpp/CMakeLists.txt"); version = "3.22.1" } }
        namespace = "net.runelite.mp"
        compileSdk = 37

        defaultConfig {
            applicationId = "net.runelite.mp"
            // minSdk = 26 is the floor flatlaf/jsvg require. Lambda invokedynamics from
            // older bytecode levels still hit "LambdaMetafactory.metafactory" hidden-API
            // failures at runtime — TODO: pre-process the injected + runelite-client jars
            // with an ASM lambda-desugar pass (mirror of desugarStringConcat) so the indys
            // become synthetic classes. Until that lands, lambda-heavy callsites in the
            // injected client crash on first invocation.
            minSdk = 26
            //noinspection EditedTargetSdkVersion
            targetSdk = 37
            // Preserve the Android release's numeric install ordering.
            versionCode = releaseVersionCode
            versionName = releaseTag
            buildConfigField("String", "RELEASE_TAG", "\"$releaseTag\"")
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
                // AGP 9 no longer ships proguard-android.txt. proguard-android-defaults.pro is its
                // -optimize file without -allowaccessmodification (see that file for why);
                // proguard-rules.pro adds -dontoptimize, so the optimizer stays off for the reason above.
                "proguard-android-defaults.pro",
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
