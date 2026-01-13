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

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.antlr" && requested.name == "antlr4-runtime") {
            useVersion("4.13.2")
            because("Truffle DSL processor requires ANTLR 4.13.2")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.coroutines.core)

    kapt(libs.truffle.dsl.processor)
    implementation(libs.truffle.api)

    implementation(libs.jtreesitter)

    testCompileOnly(libs.graal.sdk)
    testImplementation(libs.truffle.runtime)


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
