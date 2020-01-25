plugins {
    kotlin("jvm") version "1.3.61"
}

configurations {
    all {
        exclude("org.graalvm.sdk", "graal-sdk")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("org.graalvm.sdk:graal-sdk:19.3.1")
    implementation("org.graalvm.sdk:launcher-common:19.3.1")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
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
