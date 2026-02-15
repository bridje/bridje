package brj.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

interface BridjeExtension {
    val version: Property<String>
}

val SourceSet.bridje: SourceDirectorySet
    get() = extensions.getByName("bridje") as SourceDirectorySet

class GradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("bridje", BridjeExtension::class.java)

        project.plugins.apply("java")

        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer

        sourceSets.all {
            val bridjeSourceSet = project.objects.sourceDirectorySet("bridje", "$name Bridje source")
            bridjeSourceSet.srcDir("src/$name/bridje")
            bridjeSourceSet.filter.include("**/*.brj")
            extensions.add("bridje", bridjeSourceSet)
            resources.srcDir(bridjeSourceSet.srcDirs)
        }

        val mainSourceSet = sourceSets.getByName("main")

        val lspConfig = project.configurations.create("bridjeLsp") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        val replConfig = project.configurations.create("bridjeRepl") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        project.dependencies.addProvider(lspConfig.name, extension.version.map { "dev.bridje:lsp:$it" })
        project.dependencies.addProvider(replConfig.name, extension.version.map { "dev.bridje:repl:$it" })
        project.dependencies.addProvider(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, extension.version.map { "dev.bridje:language:$it" })
        project.dependencies.addProvider(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, extension.version.map { "dev.bridje:gradle-plugin:$it" })
        project.dependencies.add(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, "org.junit.platform:junit-platform-launcher:1.9.0")

        val writeBridjeTestMarker = project.tasks.register("writeBridjeTestMarker") {
            val testSourceSet = sourceSets.getByName("test")
            val markerDir = project.layout.buildDirectory.dir("bridje-test-marker")
            val bridjeTestDirs = testSourceSet.bridje.srcDirs

            inputs.files(project.files(bridjeTestDirs))
            outputs.dir(markerDir)

            doLast {
                val hasBrjFiles = bridjeTestDirs.any { dir ->
                    dir.exists() && dir.walkTopDown().any { it.extension == "brj" }
                }
                val dir = markerDir.get().asFile
                dir.mkdirs()
                if (hasBrjFiles) {
                    // Gradle only forks the test JVM when it finds .class files in testClassesDirs.
                    // Write a minimal marker so the JVM starts and our TestEngine gets discovered.
                    val markerBytes = BridjeTestMarker::class.java.getResourceAsStream("BridjeTestMarker.class")!!.readBytes()
                    val packageDir = java.io.File(dir, "brj/gradle")
                    packageDir.mkdirs()
                    java.io.File(packageDir, "BridjeTestMarker.class").writeBytes(markerBytes)
                }
            }
        }

        project.tasks.withType(Test::class.java) {
            dependsOn(writeBridjeTestMarker)
            useJUnitPlatform()

            val markerDir = project.layout.buildDirectory.dir("bridje-test-marker")
            testClassesDirs += project.files(markerDir)
            classpath += project.files(markerDir)
        }

        project.tasks.register("bridjeLsp", JavaExec::class.java) {
            group = "bridje"
            description = "Runs the Bridje LSP server"

            classpath += lspConfig
            classpath += mainSourceSet.runtimeClasspath

            mainClass.set("brj.lsp.LspServer")

            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
        }

        project.tasks.register("bridjeRepl", JavaExec::class.java) {
            group = "bridje"
            description = "Start Bridje nREPL server"

            classpath += replConfig
            classpath += mainSourceSet.runtimeClasspath

            mainClass.set("brj.repl.nrepl.NReplServerKt")

            standardInput = System.`in`
        }
    }
}
