allprojects {
    group = "dev.bridje"
    version = "0.0.3"

    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.6.20"
    id("com.palantir.graal") version "0.10.0"
}
