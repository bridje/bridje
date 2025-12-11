plugins {
    kotlin("jvm")
    application

    id("com.gradleup.shadow") version "8.3.6"
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(22))
    vendor.set(JvmVendorSpec.GRAAL_VM)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.graal.sdk)
    implementation(project(":language"))
}

java {
    modularity.inferModulePath = true
}

application {
    mainClass.set("brj.repl.ReplKt")
}

tasks.shadowJar {
    archiveBaseName.set("bridje")
    archiveVersion.set("")
    archiveClassifier.set("repl")
}
