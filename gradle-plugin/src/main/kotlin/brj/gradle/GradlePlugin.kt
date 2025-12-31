package brj.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

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