plugins { `java-library` }

dependencies {
    compileOnly(project(":client"))
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testImplementation(project(":client"))
}

sourceSets {
    main {
        java.srcDir("../third-party/rlhd/src/main/java")
        resources.srcDir("../third-party/rlhd/src/main/resources")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("dumpGlesShaderProbes") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("HdShaderProbeSources")
    args(rootProject.file("third-party/rlhd/src/main/resources/rs117/hd").absolutePath,
        layout.buildDirectory.dir("shader-probes").get().asFile.absolutePath)
}
