allprojects {
    group = "dev.bridje"
    version = "0.0.3"

    repositories {
        jcenter()
    }
}

plugins {
    kotlin("jvm") version "1.4.31"
    id("com.palantir.graal") version "0.7.2"
}
