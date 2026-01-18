plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.shadow)
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    pom {
        name.set("Bridje REPL")
        description.set("nREPL server for Bridje")
    }
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
    implementation(libs.kotlin.coroutines.core)

    implementation(libs.graal.sdk)
    implementation(project(":language"))
}

java {
    modularity.inferModulePath = true
}

application {
    mainClass.set("brj.repl.nrepl.NReplServerKt")
}

tasks.shadowJar {
    archiveBaseName.set("bridje")
    archiveVersion.set("")
    archiveClassifier.set("repl")
}
