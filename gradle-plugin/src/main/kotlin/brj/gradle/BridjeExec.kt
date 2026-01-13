package brj.gradle

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

/**
 * Gradle task type for running Bridje namespaces during development.
 * 
 * Similar to JavaExec but bridje-aware, automatically setting up classpath
 * and using brj.main with the run subcommand.
 * 
 * Usage:
 * ```kotlin
 * tasks.register<BridjeExec>("runApp") {
 *     mainNamespace.set("my:app")
 *     args("--port", "8080")
 * }
 * ```
 */
abstract class BridjeExec : JavaExec() {
    
    /**
     * The bridje namespace containing the main function to execute.
     * Required. Example: "my:app"
     */
    @get:Input
    abstract val mainNamespace: Property<String>
    
    init {
        group = "bridje"
        description = "Run a Bridje namespace"
        
        // Set up the main class
        mainClass.set("brj.main")
        
        // Configure classpath to include runtime classpath
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        classpath = sourceSets.named("main").get().runtimeClasspath
        
        // Build the arguments: run <namespace> [args...]
        // We prepend run <namespace> to any user-provided args
        argumentProviders.add {
            val namespace = mainNamespace.get()
            listOf("run", namespace)
        }
    }
}
