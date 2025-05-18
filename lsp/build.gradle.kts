import org.jetbrains.kotlin.gradle.dsl.JvmTarget.*

plugins {
    kotlin("jvm")
    kotlin("kapt")
    application
    id("com.gradleup.shadow") version "8.3.6"
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.jdk8)

    implementation(libs.lsp4j)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

java {
    modularity.inferModulePath = true

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

application {
    mainClass.set("brj.lsp.LspServer")
}

tasks.compileJava {
    options.release.set(22)
}

tasks.compileTestJava {
    options.release.set(22)
}

tasks.compileKotlin {
    compilerOptions {
        jvmTarget.set(JVM_22)
    }
}

tasks.compileTestKotlin {
    compilerOptions {
        jvmTarget.set(JVM_22)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("lsp") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("brj.lsp.LspServer")

    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err

    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(24))
        }
    )
}

tasks.shadowJar {
    archiveBaseName.set("bridje")
    archiveVersion.set("")
    archiveClassifier.set("lsp")
}
