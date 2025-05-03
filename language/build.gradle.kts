plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
}

dependencies {
    val truffleVersion = "24.2.1"

    implementation(kotlin("stdlib-jdk8"))

    compileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")
    testCompileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

sourceSets {
    main { resources { setSrcDirs(listOf("src/main/resources", "src/main/brj")) } }
    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

kotlin {
    jvmToolchain(24)
}

tasks.compileTestJava {
    options.release.set(22)
}

tasks.test {
    useJUnitPlatform()

    jvmArgs(
        "-Dtruffle.class.path.append=${sourceSets.main.get().runtimeClasspath.asPath}",
        "--add-exports", "org.graalvm.truffle/com.oracle.truffle.api.source=ALL-UNNAMED",
        "--add-exports", "org.graalvm.truffle/com.oracle.truffle.api.interop=ALL-UNNAMED"
    )
}

tasks.jar {
    group = "build"
    archiveFileName.set("brj-language.jar")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
