allprojects {
    group = "dev.bridje"
    version = "0.0.3"

    repositories {
        jcenter()
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.5.30"
    id("com.palantir.graal") version "0.9.0"
}
