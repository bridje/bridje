plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
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
}

gradlePlugin {
    plugins {
        create("bridje") {
            id = "dev.bridje"
            implementationClass = "brj.gradle.GradlePlugin"
        }
    }
}

tasks.register("generateVersionFile") {
    val outputDir = layout.buildDirectory.dir("generated/bridje")
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("brj/gradle/BridjeVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package brj.gradle

            internal object BridjeVersion {
                const val VERSION = "$version"
            }
            """.trimIndent()
        )
    }
}

sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated/bridje"))

tasks.named("compileKotlin").configure {
    dependsOn("generateVersionFile")
}
