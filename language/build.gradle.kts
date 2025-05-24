import org.jetbrains.kotlin.gradle.dsl.JvmTarget.*

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.coroutines.core)

    kapt(libs.truffle.dsl.processor)
    implementation(libs.graal.sdk)
    implementation(libs.truffle.api)
    implementation(libs.truffle.runtime)

    implementation(libs.jtreesitter)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
            srcDir("src/main/brj")
            srcDir(project(":tree-sitter").layout.buildDirectory.dir("lib"))
        }
    }

    test { resources { setSrcDirs(listOf("src/test/resources", "src/test/brj")) } }
}

java {
    modularity.inferModulePath = true

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

tasks.named("processResources") {
    dependsOn(":tree-sitter:buildTreeSitter")
}

tasks.compileJava {
    options.release.set(22)

    options.compilerArgs.add("--module-path")
    options.compilerArgs.add(classpath.asPath)

    options.compilerArgs.add("--patch-module")
    options.compilerArgs.add("bridje.language=${sourceSets["main"].output.asPath}")
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

tasks.jar {
    group = "build"
    archiveFileName.set("brj-language.jar")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
