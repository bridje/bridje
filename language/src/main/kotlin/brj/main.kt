@file:JvmName("main")

package brj

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value

/**
 * Main entry point for Bridje applications.
 * 
 * Usage: java -cp ... brj.MainKt run my:namespace args...
 */
class BridjeMain : CliktCommand(
    name = "brj.main",
    help = "Run Bridje applications"
) {
    override fun run() {
        // No-op - just shows help if no subcommand is provided
    }
}

/**
 * Run subcommand - executes a Bridje namespace's main function.
 */
class RunCommand : CliktCommand(
    name = "run",
    help = "Run a Bridje namespace's main function"
) {
    private val namespace by argument(
        name = "namespace",
        help = "Namespace containing main function (e.g., my:app)"
    )
    
    private val args by argument(
        name = "args",
        help = "Arguments to pass to the main function"
    ).multiple()
    
    override fun run() {
        try {
            Context.newBuilder()
                .allowAllAccess(true)
                .logHandler(System.err)
                .build()
                .use { context ->
                    context.enter()
                    try {
                        // Load the namespace from classpath using Source API
                        val resourcePath = nsNameToResourcePath(namespace)
                        val resourceUrl = this::class.java.classLoader.getResource(resourcePath)
                            ?: error("Namespace not found on classpath: $namespace (looked for $resourcePath)")
                        
                        val source = Source.newBuilder("bridje", resourceUrl).build()
                        context.eval(source)
                        
                        // Get the namespace from bindings
                        val bindings = context.getBindings("bridje")
                        if (!bindings.hasMember(namespace)) {
                            echo("Error: Namespace '$namespace' not found", err = true)
                            throw ProgramResult(1)
                        }
                        
                        val ns = bindings.getMember(namespace)
                        
                        // Check if the namespace has a 'main' function
                        if (!ns.hasMember("main")) {
                            echo("Error: Namespace '$namespace' does not have a 'main' function", err = true)
                            throw ProgramResult(1)
                        }
                        
                        val mainFn = ns.getMember("main")
                        if (!mainFn.canExecute()) {
                            echo("Error: 'main' in namespace '$namespace' is not a function", err = true)
                            throw ProgramResult(1)
                        }
                        
                        // Convert args to GraalVM Values
                        val graalArgs = args.map { context.asValue(it) }.toTypedArray()
                        
                        // Create a Bridje vector from the arguments
                        val argsVector = createVector(context, graalArgs)
                        
                        // Invoke the main function with the vector
                        mainFn.execute(argsVector)
                        
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
    
    private fun createVector(context: Context, args: Array<Value>): Value {
        // Build a vector literal and evaluate it
        val vectorCode = buildString {
            append("[")
            args.forEachIndexed { index, value ->
                if (index > 0) append(", ")
                // Quote string values
                append('"')
                append(escapeString(value.asString()))
                append('"')
            }
            append("]")
        }
        return context.eval("bridje", vectorCode)
    }
    
    private fun escapeString(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}

fun main(args: Array<String>) {
    BridjeMain()
        .subcommands(RunCommand())
        .main(args)
}
