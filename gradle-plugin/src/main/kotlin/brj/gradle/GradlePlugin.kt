package brj.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

class GradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("java")

        val sourcesSets = project.extensions.getByName("sourceSets") as SourceSetContainer

        val mainSourceSet = sourcesSets.getByName("main")
        mainSourceSet.java.srcDir("src/main/bridje")

        val testSourceSet = sourcesSets.getByName("test")
        testSourceSet.java.srcDir("src/test/bridje")

        val lspConfig = project.configurations.create("bridjeLsp") {
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        project.dependencies.add(lspConfig.name, "dev.bridje:lsp:${BridjeVersion.VERSION}")

        project.tasks.register("bridjeLsp", JavaExec::class.java) {
            group = "application"
            description = "Runs the Bridje LSP server"

            classpath += project.configurations.getByName("bridjeLsp")
            classpath += mainSourceSet.runtimeClasspath

            mainClass.set("brj.lsp.LspServer")

            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
        }
    }
}