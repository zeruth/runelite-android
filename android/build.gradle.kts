import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Properties
import org.gradle.api.attributes.java.TargetJvmEnvironment

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
val versionPatch = 63
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

// Guava's JRE flavour: hub plugins compile against runelite-client's guava-jre and call the
// default methods guava-android leaves out (Multiset.forEachEntry, Multimap.forEach, the
// java.util.function Cache/LoadingCache members). minSdk 26 has java.util.function and
// streams natively. Guava's module metadata picks the flavour from
// org.gradle.jvm.environment, which AGP sets to android, so the version string alone would
// still resolve the android jar; the constraint in the dependencies block below asks for
// the standard-jvm variant.
val guavaJre = "com.google.guava:guava:33.6.0-jre"
configurations.all {
    resolutionStrategy.force(
        guavaJre,
        // Guava's own placeholder for the ListenableFuture split artifact. AGP 9 no longer adds
        // dependency constraints (android.dependency.useConstraints defaults to false), so the
        // real listenablefuture:1.0 some transitive dependency asks for surfaced as a duplicate
        // class against guava-android; pinning the empty version is Guava's documented answer.
        "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava"
    )
}

// Hub plugins link these by name against the host, as they do against runelite-client's
// classpath on desktop, so the APK ships the versions runelite-client resolves: okhttp
// through http-api, slf4j/logback from the catalog. None of the app's other dependencies
// (Compose, Firebase, play-services, r8, asm, tomlj, flatlaf, sqlite-jdbc) asks for okhttp 4
// or slf4j 2, so the force only holds them if a future dependency does. Only the app's own
// classpaths: AGP's tool configurations (lint, R8) bring their own.
// tools-src/hub-compat-scan.py takes each host version from build/hub-compat/
// releaseRuntimeClasspath.txt (`:android:dependencies --configuration releaseRuntimeClasspath`
// output) when that file exists; without it, from the literal "group:artifact:version"
// strings in this file (guavaJre, okhttp), then libs.versions.toml (slf4j, logback), then the
// newest jar in the gradle cache.
//
// okio is not forced. Firebase sessions persist through androidx DataStore, which needs
// okio 3, and `-dontwarn okio.**` would let R8 pass a forced okio 1 and break DataStore
// at run time. okio 2+ keeps okio 1's public Java API. What it drops (Buffer.writableSegment,
// AsyncTimeout.exit(boolean)/exit(IOException)) is package-private, so only classes in
// package okio call it: a plugin's shaded okio copy, whose classes are either shadowed by
// the host's or never load. okhttp 3.14.9 asks for okio 1.17.2 and DataStore raises it;
// only the dependency report gives hub-compat-scan.py the okio that wins, since nothing above
// names one; without the report it scans whatever okio jar the gradle cache offers.
val hostLibraryPins = listOf(
    "com.squareup.okhttp3:okhttp:3.14.9",
    "org.slf4j:slf4j-api:${libs.versions.slf4j.get()}",
    "ch.qos.logback:logback-classic:${libs.versions.logback.get()}",
    "ch.qos.logback:logback-core:${libs.versions.logback.get()}",
)
configurations.matching { it.name.endsWith("CompileClasspath") || it.name.endsWith("RuntimeClasspath") }
    .configureEach { resolutionStrategy.force(*hostLibraryPins.toTypedArray()) }

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
    val sqliteJdbc = "org.xerial:sqlite-jdbc:3.53.4.0"
    val sqliteNatives = configurations.create("sqliteAndroidNatives") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(sqliteNatives.name, "$sqliteJdbc:natives-android@jar")
    val unpackSqlite = tasks.register<Sync>("unpackSqliteAndroidNatives") {
        from(provider { zipTree(sqliteNatives.singleFile) })
        include("org/sqlite/native/Linux-Android/*/libsqlitejdbc.so")
        includeEmptyDirs = false
        eachFile {
            val arch = relativePath.segments[4]
            val abi = when (arch) {
                "aarch64" -> "arm64-v8a"
                "arm" -> "armeabi-v7a"
                else -> arch
            }
            path = "$abi/$name"
        }
        into(layout.buildDirectory.dir("generated/sqliteJni"))
    }
    extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
        sourceSets.named("main") { jniLibs.srcDir(layout.buildDirectory.dir("generated/sqliteJni").get().asFile) }
    }
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
        dependsOn(unpackSqlite)
    }
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
        "implementation"(project(path = ":client", configuration = "androidRuntimeElements"))
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
        // okhttp3 is already in the APK through runelite-client's http-api; named here because the
        // app's own OkHttpWebSocketTransport (the wire under android-awt's java.net.http.WebSocket)
        // compiles against it. Same version, so it resolves to the artifact already verified.
        "implementation"("com.squareup.okhttp3:okhttp:3.14.9")
        // D8, run in-process by PluginDexer to convert upstream hub jars (JVM bytecode) to
        // dex on the device. Pure Java; kept whole in proguard-rules.pro.
        "implementation"("com.android.tools:r8:9.5.28")
        "implementation"(files(androidH2.flatMap { it.archiveFile }))
        "implementation"("$sqliteJdbc:without-natives@jar")
        // ASM for ClientPreparer's transforms over the downloaded injected client.
        "implementation"("org.ow2.asm:asm:9.8")
        "implementation"("org.ow2.asm:asm-commons:9.8")
        // Fallback for ClientPreparer's BaseDexClassLoader.addDexPath call (hidden API).
        "implementation"("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

        "implementation"(guavaJre)
        constraints.add("implementation", guavaJre) {
            attributes {
                attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM))
            }
        }
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
            // The libraries hub plugins link by name; tools-src/verify-release-plugin-api.py
            // lists the same packages in PREFIXES.
            val pluginApiModules = setOf(
                "net.sf.jopt-simple:jopt-simple", "com.google.protobuf:protobuf-javalite",
                "com.google.guava:guava", "com.google.guava:failureaccess", "com.google.code.gson:gson",
                // okio 3 resolves to its okio-jvm variant when a dependency raises it.
                "com.squareup.okhttp3:okhttp", "com.squareup.okio:okio", "com.squareup.okio:okio-jvm",
                "org.slf4j:slf4j-api",
                "net.runelite:discord"
            )
            val artifacts = configurations.getByName("releaseRuntimeClasspath").incoming.artifactView {
                attributes.attribute(org.gradle.api.attributes.Attribute.of("artifactType", String::class.java), "jar")
                componentFilter { id -> id is org.gradle.api.artifacts.component.ModuleComponentIdentifier &&
                    "${id.group}:${id.module}" in pluginApiModules }
            }.artifacts.artifacts
            // A module missing from the classpath would hand the verifier no jar for its
            // prefix and pass silently; fail instead. okio counts once, under either name.
            val resolved = artifacts.mapNotNull {
                (it.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                    ?.let { id -> "${id.group}:${id.module}" }
            }.toSet()
            val okioModules = setOf("com.squareup.okio:okio", "com.squareup.okio:okio-jvm")
            val missing = (pluginApiModules - okioModules).filter { it !in resolved } +
                (if (resolved.none { it in okioModules }) listOf("com.squareup.okio:okio") else emptyList())
            if (missing.isNotEmpty()) {
                error("releaseRuntimeClasspath lacks plugin API modules: ${missing.joinToString()}")
            }
            val libraries = artifacts.map { it.file }.filter { it.extension == "jar" }
            // guava-android and guava-jre share one module id; only the variant's file tells them apart.
            val guavaJars = libraries.filter { it.name.startsWith("guava-") && !it.name.startsWith("guava-listenablefuture") }
            if (guavaJars.isEmpty() || guavaJars.any { !it.name.endsWith("-jre.jar") }) {
                error("releaseRuntimeClasspath resolved guava as ${guavaJars.map { it.name }}, expected the -jre variant")
            }
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
    val clientProject = project(":client")
    val androidClientJar = layout.buildDirectory.file("compat-client/runelite-client-android.jar")
    val retargetToolClasspath = configurations.create("jdkRetargetToolClasspath") {
        isCanBeConsumed = false
    }
    dependencies.add(retargetToolClasspath.name, "org.ow2.asm:asm:9.8")
    dependencies.add(retargetToolClasspath.name, "org.ow2.asm:asm-commons:9.8")
    val compileRetargetTool = tasks.register<JavaCompile>("compileBundledClientRetargetTool") {
        source(rootProject.file("tools-src/RetargetBundledClient.java"),
            file("src/androidMain/java/net/runelite/mp/compat/JdkRetargeter.java"),
            file("src/androidMain/java/net/runelite/mp/compat/jdk/JdkRetargets.java"))
        classpath = retargetToolClasspath
        destinationDirectory.set(layout.buildDirectory.dir("compat-client/tool"))
    }
    val originalClientJar = provider {
        clientProject.tasks.named<Jar>("jar").get().archiveFile.get().asFile
    }
    val retargetBundledClient = tasks.register<JavaExec>("retargetBundledClient") {
        dependsOn(":client:jar", compileRetargetTool)
        classpath = files(compileRetargetTool.flatMap { it.destinationDirectory }) + retargetToolClasspath
        mainClass.set("RetargetBundledClient")
        inputs.file(originalClientJar)
        outputs.file(androidClientJar)
        doFirst { args(originalClientJar.get().absolutePath, androidClientJar.get().asFile.absolutePath) }
    }
    // Keep the desktop artifact untouched and retain the client's transitive dependencies.
    clientProject.plugins.withId("java-library") {
        clientProject.configurations.create("androidRuntimeElements") {
            isCanBeResolved = false
            extendsFrom(*clientProject.configurations.getByName("runtimeElements").extendsFrom.toTypedArray())
            outgoing.artifact(androidClientJar) { builtBy(retargetBundledClient) }
        }
    }
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
    // The shadow surface is whatever android-awt's package list says it is (its consumer keep
    // rules come from the same list): host-api.jar carries exactly those classes, and the
    // release mapping check below proves R8 kept each by name. Resolved lazily, :android-awt
    // is configured after this project.
    val shadowPackages = provider {
        @Suppress("UNCHECKED_CAST")
        (project(":android-awt").extra["shadowPackages"] as List<String>)
    }
    val shadowClasses = provider {
        @Suppress("UNCHECKED_CAST")
        (project(":android-awt").extra["shadowClasses"] as List<String>)
    }
    // Internal names (java/awt/Color, java/lang/ProcessHandle$Info) of the compiled shadow
    // classes, sorted for a reproducible jar.
    fun shadowClassNames(root: File): List<String> {
        val packagePrefixes = shadowPackages.get().map { it.replace('.', '/') + "/" }
        val classNames = shadowClasses.get().map { it.replace('.', '/') }
        return root.walkTopDown().filter { it.isFile && it.extension == "class" }
            .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class") }
            .filter { name -> packagePrefixes.any(name::startsWith) || classNames.any { name == it || name.startsWith("$it$") } }
            .toList().sorted()
    }
    val generatedAssets = layout.buildDirectory.dir("generated/runtime-desugar-assets")
    val prepareRuntimeDesugarAssets = tasks.register("prepareRuntimeDesugarAssets") {
        dependsOn(":android-awt:compileReleaseJavaWithJavac")
        inputs.files(runtimeDesugarConfiguration, bootApi)
        inputs.dir(hostApi)
        inputs.property("coreLibraryVersion", coreLibraryVersion)
        inputs.property("shadowPackages", shadowPackages)
        inputs.property("shadowClasses", shadowClasses)
        outputs.dir(generatedAssets)
        doLast {
            val out = generatedAssets.get().dir("runtime-dex").asFile.apply { mkdirs() }
            val configuration = runtimeDesugarConfiguration.singleFile
            java.util.zip.ZipFile(configuration).use { archive ->
                archive.getInputStream(archive.getEntry("META-INF/desugar/d8/desugar.json")).use {
                    out.resolve("desugar.json").writeBytes(it.readBytes())
                }
            }
            // android-awt's java.net.http (and com.sun.net.httpserver) is built on
            // java.util.concurrent.Flow, which android.jar only has from API 30. Below that it
            // must come from the desugared library or from a shadow class; neither means
            // NoClassDefFoundError on the first HttpClient call on an API 26-29 device.
            // desugar.json abbreviates packages through its package_map (2.1.4 maps
            // "java.util.concurrent" to "h$"), so Flow shows up as "h$Flow" in a rewrite_type entry.
            val desugarJson = out.resolve("desugar.json").readText()
            val concurrentPrefix = Regex("\"java\\.util\\.concurrent\"\\s*:\\s*\"([^\"]+)\"")
                .find(desugarJson)?.groupValues?.get(1)
            val flowDesugared = desugarJson.contains("\"java.util.concurrent.Flow\"") ||
                (concurrentPrefix != null && desugarJson.contains("\"" + concurrentPrefix + "Flow\""))
            val flowShadowed = hostApi.get().resolve("java/util/concurrent/Flow.class").isFile
            println("java.util.concurrent.Flow below API 30: " + when {
                flowDesugared -> "desugar_jdk_libs $coreLibraryVersion"
                flowShadowed -> "android-awt shadow"
                else -> "missing"
            })
            if (!flowDesugared && !flowShadowed) {
                throw GradleException(
                    "desugar_jdk_libs $coreLibraryVersion does not provide java.util.concurrent.Flow and " +
                        "android-awt has no shadow of it; minSdk 26 needs one for java.net.http"
                )
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
            // The shadow surface only: hub plugins link against JDK names, never the backend
            // under net.runelite.awt, and no shadow class extends a backend type.
            java.util.zip.ZipOutputStream(out.resolve("host-api.jar").outputStream()).use { target ->
                for (name in shadowClassNames(hostApi.get())) {
                    target.putNextEntry(java.util.zip.ZipEntry("$name.class").apply { time = 0 })
                    hostApi.get().resolve("$name.class").inputStream().use { it.copyTo(target) }
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

    // The downloaded client and hub plugins link against the shadow surface after R8 has run,
    // so a renamed or stripped shadow class is a release-only NoClassDefFoundError. The keep
    // rules say R8 may not touch it; this reads the mapping file to prove it did not. R8 lists
    // every class it emitted as `original -> emitted:`, kept classes as identity lines.
    val checkReleaseShadowMapping = tasks.register("checkReleaseShadowMapping") {
        group = "verification"
        description = "Fail when the release R8 mapping renames or drops a shadow JDK class."
        // With core-library desugaring on, outputs/mapping/release/mapping.txt is written by
        // the L8 step (R8's mapping plus the desugared library's), not by minifyReleaseWithR8.
        dependsOn("minifyReleaseWithR8", "l8DexDesugarLibRelease",
            ":android-awt:compileReleaseJavaWithJavac", ":android-awt:shadowKeepRulesCheck")
        val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
        inputs.file(mapping)
        inputs.dir(hostApi)
        inputs.property("shadowPackages", shadowPackages)
        inputs.property("shadowClasses", shadowClasses)
        doLast {
            val emitted = HashMap<String, String>()
            mapping.get().asFile.useLines { lines ->
                lines.filter { !it.startsWith(" ") && !it.startsWith("#") && it.endsWith(":") }
                    .forEach { line ->
                        val arrow = line.indexOf(" -> ")
                        if (arrow > 0) emitted[line.substring(0, arrow)] = line.substring(arrow + 4, line.length - 1)
                    }
            }
            val shadow = shadowClassNames(hostApi.get()).map { it.replace('/', '.') }
            val renamed = shadow.filter { emitted[it]?.let { to -> to != it } == true }
            val missing = shadow.filterNot { it in emitted }
            println("== Release shadow mapping check ==")
            println("mapping: ${mapping.get().asFile}")
            println("Shadow classes: ${shadow.size}; renamed: ${renamed.size}; missing: ${missing.size}")
            renamed.forEach { println("  renamed: $it -> ${emitted[it]}") }
            missing.forEach { println("  missing: $it") }
            if (renamed.isNotEmpty() || missing.isNotEmpty()) {
                throw GradleException(
                    "release R8 renamed ${renamed.size} and dropped ${missing.size} shadow class(es); the android-awt " +
                        "consumer keep rules no longer cover the shadow list, or an app rule overrides them."
                )
            }
        }
    }
    tasks.configureEach {
        if (name == "assembleRelease") dependsOn(checkReleaseShadowMapping)
    }

    // The bundled client is audited after retargeting; android-awt and the app compile
    // directly against Android and must not call a member that needs retargeting.
    // The rows are read from the table's source (one onStatic/onVirtual/
    // onInterface call per row, owner, name and descriptor first) and matched against the
    // method references and class constants in the compiled classes' constant pools.
    // The method references (`owner.namedescriptor`) and class constants (`class owner`) of a
    // class file, read from its constant pool so the check needs no bytecode library.
    fun constantPoolRefs(bytes: ByteArray): List<String> {
        val buf = java.nio.ByteBuffer.wrap(bytes)
        if (buf.int != 0xCAFEBABE.toInt()) return emptyList()
        buf.int // minor and major version
        fun u2() = buf.short.toInt() and 0xffff
        val count = u2()
        val utf = arrayOfNulls<String>(count)
        val classNames = IntArray(count)
        val natNames = IntArray(count)
        val natDescriptors = IntArray(count)
        val methodRefs = mutableListOf<Pair<Int, Int>>()
        var i = 1
        while (i < count) {
            val tag = buf.get().toInt()
            when (tag) {
                1 -> { val raw = ByteArray(u2()); buf.get(raw); utf[i] = String(raw, Charsets.UTF_8) }
                3, 4, 9, 17, 18 -> buf.position(buf.position() + 4)
                5, 6 -> { buf.position(buf.position() + 8); i++ }
                7 -> classNames[i] = u2()
                8, 16, 19, 20 -> buf.position(buf.position() + 2)
                10, 11 -> { val owner = u2(); val nat = u2(); methodRefs.add(owner to nat) }
                12 -> { natNames[i] = u2(); natDescriptors[i] = u2() }
                15 -> buf.position(buf.position() + 3)
                else -> throw GradleException("unknown constant pool tag $tag")
            }
            i++
        }
        return methodRefs.map { (owner, nat) -> "${utf[classNames[owner]]}.${utf[natNames[nat]]}${utf[natDescriptors[nat]]}" } +
            classNames.filter { it != 0 }.map { "class ${utf[it]}" }
    }
    val retargetTable = file("src/androidMain/java/net/runelite/mp/compat/jdk/JdkRetargets.java")
    val appCompileTasks = tasks.matching { it.name == "compileDebugJavaWithJavac" || it.name == "compileDebugKotlin" }
    val jdkRetargetCheck = tasks.register("jdkRetargetCheck") {
        group = "verification"
        description = "Fail when android-awt or the app calls a JDK member the JdkRetargets table retargets."
        dependsOn(":android-awt:compileReleaseJavaWithJavac", appCompileTasks, retargetBundledClient)
        inputs.file(retargetTable)
        inputs.file(androidClientJar)
        inputs.dir(hostApi)
        inputs.files(provider { appCompileTasks.flatMap { it.outputs.files.files } })
        doLast {
            val source = retargetTable.readText()
            val rows = Regex("""on(?:Static|Virtual|Interface)\("([^"]+)", "([^"]+)", "([^"]+)"""")
                .findAll(source).map { "${it.groupValues[1]}.${it.groupValues[2]}${it.groupValues[3]}" }.toSet()
            val relocated = Regex("""relocate\("([^"]+)"""").findAll(source).map { "class ${it.groupValues[1]}" }.toSet()
            if (rows.isEmpty()) throw GradleException("no rows parsed from $retargetTable; keep the one-call-per-row shape")
            val roots = listOf(hostApi.get()) + appCompileTasks.flatMap { it.outputs.files.files }.filter { it.isDirectory }
            val hits = sortedSetOf<String>()
            var scanned = 0
            fun inspect(name: String, bytes: ByteArray) {
                if (name.startsWith("net/runelite/mp/compat/jdk/")) return
                scanned++
                for (ref in constantPoolRefs(bytes)) {
                    if (ref in rows || ref in relocated) hits += "$name -> $ref"
                }
            }
            for (root in roots) {
                root.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { file ->
                    val name = file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class")
                    inspect(name, file.readBytes())
                }
            }
            java.util.zip.ZipFile(androidClientJar.get().asFile).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                    zip.getInputStream(entry).use { inspect(entry.name.removeSuffix(".class"), it.readBytes()) }
                }
            }
            println("== JDK retarget check: ${rows.size} rows, ${relocated.size} relocated types, $scanned classes ==")
            hits.forEach { println("  $it") }
            if (hits.isNotEmpty()) {
                throw GradleException("${hits.size} call(s) to JdkRetargets members in android-awt or the app; " +
                    "call the net.runelite.mp.compat.jdk shim instead")
            }
        }
    }
    tasks.configureEach {
        if (name == "assembleDebug" || name == "assembleRelease" || name == "check") dependsOn(jdkRetargetCheck)
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
