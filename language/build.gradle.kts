plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    pom {
        name.set("Bridje Language")
        description.set("Bridje programming language runtime")
    }
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(22))
    vendor.set(JvmVendorSpec.GRAAL_VM)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.coroutines.core)

    kapt(libs.truffle.dsl.processor)
    implementation(libs.truffle.api)
    implementation(libs.graal.sdk)

    implementation(libs.clikt)

    implementation(libs.jtreesitter)

    implementation(libs.truffle.runtime)

    testImplementation(libs.clikt.testing)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sourceSets {
    main {
        resources {
            srcDir("src/main/brj")
            srcDir(project(":tree-sitter").layout.buildDirectory.dir("lib"))
        }
    }

    test { resources.srcDir("src/test/brj") }
}

tasks.named("processResources") {
    dependsOn(":tree-sitter:buildTreeSitter", ":tree-sitter:copyQueries")
}

tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(":tree-sitter:buildTreeSitter", ":tree-sitter:copyQueries")
}

tasks.test {
    useJUnitPlatform()
}
