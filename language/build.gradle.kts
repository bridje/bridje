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

fun truffle(module: String) = "org.graalvm.truffle:truffle-$module:19.3.0"

dependencies {
    antlr("org.antlr:antlr4:4.7.2")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect")) // Only for the instantiate hack
    implementation(truffle("api"))

    kapt(truffle("dsl-processor"))

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

sourceSets {
    main { resources { setSrcDirs(listOf("src/main/resources", "src/main/brj")) } }
    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-no-listener")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    group = "build"
    archiveBaseName.set("brj-language")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
