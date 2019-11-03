group = "dev.bridje"
version = "0.0-SNAPSHOT"

val kotlinVersion = "1.3.50"
val graalVersion = "19.2.0"

plugins {
    kotlin("jvm") version "1.3.50"
    kotlin("kapt") version "1.3.50"
    antlr
}

repositories {
    jcenter()
}

fun truffle(module: String) = "org.graalvm.truffle:truffle-$module:19.2.0"

dependencies {
    antlr("org.antlr:antlr4:4.7.2")

    implementation(kotlin("stdlib"))
    implementation(truffle("api"))

    kapt(truffle("dsl-processor"))

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

sourceSets {
    main { resources { setSrcDirs(listOf("src/main/resources", "src/main/brj")) } }
    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-no-listener")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    group = "build"
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.register("component", type = Jar::class) {
    group = "build"
    description = "Build component JAR suitable for adding into GraalVM"
    baseName = "${project.name}-component"

    dependsOn("jar")

    manifest {
        attributes(
            "Bundle-Symbolic-Name" to "brj",
            "Bundle-RequireCapability" to """org.graalvm; filter:="(&(graalvm_version=19.2.0)(os_arch=amd64))"""",
            "Bundle-Name" to "Bridje",
            "Bundle-Version" to "0.0.1",
            "x-GraalVM-Polyglot-Part" to "True")
    }

    into("jre/languages/") {
        from(tasks.jar.get().outputs)
    }
}
