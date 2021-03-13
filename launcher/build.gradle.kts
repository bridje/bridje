plugins {
    kotlin("jvm")
}

dependencies {
    module(":language")
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("org.graalvm.sdk:graal-sdk:21.0.0.2")
    implementation("org.graalvm.sdk:launcher-common:21.0.0.2")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
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

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    group = "build"
    archiveFileName.set("brj-launcher.jar")

    manifest {
        attributes("Main-Class" to "brj.BridjeLauncher")
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
