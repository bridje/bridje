plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    val truffleVersion = "21.0.0"

    implementation(kotlin("stdlib-jdk8"))

    compileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")
    testCompileOnly("org.graalvm.truffle:truffle-api:${truffleVersion}")

    kapt("org.graalvm.truffle:truffle-dsl-processor:${truffleVersion}")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}

sourceSets {
    main { resources { setSrcDirs(listOf("src/main/resources", "src/main/brj")) } }
    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

tasks.compileKotlin {
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
