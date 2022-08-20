plugins {
    kotlin("jvm")
    kotlin("kapt")
    antlr
}

dependencies {
    val truffleVersion = "22.1.0"

    antlr("org.antlr:antlr4:4.9.2")
    implementation("org.antlr:antlr4-runtime:4.9.2")

    implementation(kotlin("stdlib-jdk8"))

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.14.0")

    compileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")
    testCompileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")

    kapt("org.graalvm.truffle:truffle-dsl-processor:${truffleVersion}")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

sourceSets {
    main { resources { setSrcDirs(listOf("src/main/resources", "src/main/brj")) } }
    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

configurations {
    runtimeClasspath {
        exclude("org.antlr", "antlr4")
    }
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-no-listener", "-package", "bridje.antlr")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Dtruffle.class.path.append=${sourceSets.main.get().runtimeClasspath.asPath}",
        "--add-exports", "org.graalvm.truffle/com.oracle.truffle.api.source=ALL-UNNAMED"
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
