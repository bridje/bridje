plugins {
    kotlin("jvm")
}

dependencies {
    module(":language")
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("org.graalvm.sdk:graal-sdk:22.1.0")
    implementation("org.graalvm.sdk:launcher-common:22.1.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.compileKotlin {
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
}

tasks.jar {
    group = "build"
    archiveFileName.set("brj-launcher.jar")

    manifest {
        attributes("Main-Class" to "brj.BridjeLauncher")
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
