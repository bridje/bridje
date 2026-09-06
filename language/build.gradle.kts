import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("com.vanniktech.maven.publish")

    // not applied: we only want the ShadowJar task type, for `relocatedTruffleProcessor` below
    alias(libs.plugins.shadow) apply false
}

mavenPublishing {
    pom {
        name.set("Bridje Language")
        description.set("Bridje programming language runtime")
    }
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.GRAAL_VM)
}

// Gradle's worker classpath carries antlr4-runtime 4.7.2 and sits above kapt's processor path,
// so the 4.13.2 runtime bundled in truffle-dsl-processor never loads
// and the DSL expression parser's serialised ATN fails to deserialise.
// Renaming ANTLR's package inside the processor jar takes it out of the collision.
val truffleProcessor: Configuration by configurations.creating

val relocatedTruffleProcessor by tasks.registering(ShadowJar::class) {
    configurations.set(listOf(truffleProcessor))
    archiveClassifier.set("relocated")
    relocate("org.antlr", "brj.shaded.org.antlr")
}

dependencies {
    truffleProcessor(libs.truffle.dsl.processor)

    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.coroutines.core)

    kapt(files(relocatedTruffleProcessor))
    implementation(libs.truffle.api)
    implementation(libs.graal.sdk)

    implementation(libs.clikt)

    implementation(libs.jtreesitter)

    implementation(libs.truffle.runtime)

    implementation(libs.junit.platform.engine)

    testImplementation(libs.clikt.testing)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.graal.js)
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

kapt {
    javacOptions {
        option("--enable-preview")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

tasks.test {
    useJUnitPlatform()
}
