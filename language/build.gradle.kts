plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
}

dependencies {
    val truffleVersion = "24.2.1"

    implementation(kotlin("stdlib-jdk8"))
    kapt("org.graalvm.truffle:truffle-dsl-processor:${truffleVersion}")

    implementation("org.graalvm.truffle:truffle-api:${truffleVersion}")
    implementation("org.graalvm.sdk:graal-sdk:${truffleVersion}")

    testImplementation(kotlin("test-junit"))
    testImplementation(project(":language"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

sourceSets {
    main { resources { setSrcDirs(listOf("src/main/resources", "src/main/brj")) } }
    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

java {
    modularity.inferModulePath = true
}

kotlin {
    jvmToolchain(24)
}

tasks.compileJava {
    options.release.set(22)

    options.compilerArgs.add("--module-path")
    options.compilerArgs.add(classpath.asPath)

    options.compilerArgs.add("--patch-module")
    options.compilerArgs.add("bridje.language=${sourceSets["main"].output.asPath}")
}

tasks.compileTestJava {
    options.release.set(22)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    group = "build"
    archiveFileName.set("brj-language.jar")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
