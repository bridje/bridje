plugins {
    kotlin("jvm") version "1.3.50"
    kotlin("kapt") version "1.3.50"
    antlr
}

dependencies {
    val truffleVersion = "19.3.0"

    antlr("org.antlr:antlr4:4.7.2")

    compile("org.graalvm.truffle:truffle-api:${truffleVersion}")
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    kapt("org.graalvm.truffle:truffle-dsl-processor:${truffleVersion}")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:5.5.2")
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

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

tasks.compileTestKotlin {
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Dtruffle.class.path.append=${sourceSets.main.get().runtimeClasspath.asPath}",
        "--add-exports=org.graalvm.truffle/com.oracle.truffle.api.interop=ALL-UNNAMED",
        "--add-exports=org.graalvm.truffle/com.oracle.truffle.api.source=ALL-UNNAMED",
        "--add-exports=org.graalvm.truffle/com.oracle.truffle.api=ALL-UNNAMED")
}

tasks.jar {
    group = "build"
    archiveFileName.set("brj-language.jar")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}