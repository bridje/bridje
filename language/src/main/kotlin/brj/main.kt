@file:JvmName("main")

package brj

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.Context as GraalContext

/**
 * Main entry point for Bridje applications.
 * 
 * Usage: java -cp ... brj.MainKt run my:namespace args...
 */
class BridjeMain : CliktCommand(name = "brj.main") {
    override fun help(context: Context) = "Run Bridje applications"
    
    override fun run() {
        // No-op - just shows help if no subcommand is provided
    }
}

class RunCommand : CliktCommand(name = "run") {
    override fun help(context: Context) = "Run a Bridje namespace's main function"

    private val namespace by argument()
    private val args by argument().multiple()

    override fun run() {
        try {
            GraalContext.newBuilder()
                .allowAllAccess(true)
                .logHandler(System.err)
                .build()
                .use { context ->
                    context.enter()
                    try {
                        val resourcePath = nsNameToResourcePath(namespace)
                        val resourceUrl = this::class.java.classLoader.getResource(resourcePath)
                            ?: error("Namespace not found on classpath: $namespace (looked for $resourcePath)")

                        val source = Source.newBuilder("bridje", resourceUrl).build()
                        val ns = context.eval(source)

                        if (!ns.hasMember("main")) {
                            echo("Error: Namespace '$namespace' does not have a 'main' function", err = true)
                            throw ProgramResult(1)
                        }

                        val mainFn = ns.getMember("main")
                        if (!mainFn.canExecute()) {
                            echo("Error: 'main' in namespace '$namespace' is not a function", err = true)
                            throw ProgramResult(1)
                        }

                        // Pass args list directly - GraalVM will convert to interop list
                        mainFn.execute(args)
                    } finally {
                        context.leave()
                    }
                }
        } catch (e: PolyglotException) {
            if (e.isExit) {
                throw ProgramResult(e.exitStatus)
            } else {
                echo("Error executing main function:", err = true)
                e.printStackTrace(System.err)
                throw ProgramResult(1)
            }
        } catch (e: ProgramResult) {
            throw e
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            e.printStackTrace(System.err)
            throw ProgramResult(1)
        }
    }

    private fun nsNameToResourcePath(nsName: String): String =
        nsName.replace(':', '/') + ".brj"
}

fun main(args: Array<String>) {
    BridjeMain()
        .subcommands(RunCommand())
        .main(args)
}
