plugins {
    kotlin("jvm")

    alias(libs.plugins.shadow)
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(22))
    vendor.set(JvmVendorSpec.GRAAL_VM)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":language"))

    compileOnly(libs.graal.sdk)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.shadowJar {
    archiveBaseName.set("bridje")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to "brj.Launcher"
        )
    }

    minimize()
}
