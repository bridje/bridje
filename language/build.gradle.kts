plugins {
    kotlin("jvm")
    kotlin("kapt")
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

    implementation(libs.jtreesitter)

    testCompileOnly(libs.graal.sdk)
    testImplementation(libs.truffle.runtime)


    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
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

java {
    modularity.inferModulePath = true
}

tasks.named("processResources") {
    dependsOn(":tree-sitter:buildTreeSitter", ":tree-sitter:copyQueries")
}

tasks.compileJava {
    options.compilerArgs.add("--module-path")
    options.compilerArgs.add(classpath.asPath)

    options.compilerArgs.add("--patch-module")
    options.compilerArgs.add("bridje.language=${sourceSets["main"].output.asPath}")
}

tasks.test {
    useJUnitPlatform()
}
