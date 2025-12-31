plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.vanniktech.maven.publish") version "0.35.0" apply false
}

val brjVersion = System.getenv("BRJ_VERSION") ?: "0.x-SNAPSHOT"

allprojects {
    group = "dev.bridje"
    version = brjVersion

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.withPlugin("signing") {
        configure<SigningExtension> {
            useGpgCmd()
        }
    }
}
