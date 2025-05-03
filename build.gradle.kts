plugins {
    kotlin("jvm") version "2.0.20"
}

allprojects {
    group = "dev.bridje"

    repositories {
        mavenCentral()
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}
