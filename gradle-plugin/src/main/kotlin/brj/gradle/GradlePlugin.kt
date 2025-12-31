package brj.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

interface BridjeExtension {
    val version: Property<String>
}

class GradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("bridje", BridjeExtension::class.java)

        project.plugins.apply("java")

        val sourcesSets = project.extensions.getByName("sourceSets") as SourceSetContainer

        val mainSourceSet = sourcesSets.getByName("main")
        mainSourceSet.java.srcDir("src/main/bridje")

        val testSourceSet = sourcesSets.getByName("test")
        testSourceSet.java.srcDir("src/test/bridje")

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