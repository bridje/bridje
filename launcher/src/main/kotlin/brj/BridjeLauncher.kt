package brj

import brj.BridjeLauncher.EvalScript.*
import org.graalvm.launcher.AbstractLanguageLauncher
import org.graalvm.options.OptionCategory
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import java.io.File
import java.lang.String.format

class BridjeLauncher : AbstractLanguageLauncher() {

    private sealed class EvalScript {
        data class EvalFile(val file: File) : EvalScript()
        data class EvalString(val str: String) : EvalScript()
        data class EvalMain(val ns: String) : EvalScript()
    }

    private val evalScripts = mutableListOf<EvalScript>()
    private var lsp: Boolean = false
    private lateinit var args: List<String>

    private fun printOption(opt: String, description: String) {
        println(format("  %-44s %s", opt, description))
    }

    override fun printHelp(maxCategory: OptionCategory?) {
        println("Usage: brj [OPTION]... -- [ARGS]...")
        println()
        println("Bridje Options:")
        printOption("-f, --file FILE", "File to execute.")
        printOption("-e, --eval CODE|-", "Evaluate code snippet, or '-' to read from stdin")
        printOption("-m, --main NS", "Evaluates the `main` function in the given namespace.")
        printOption("--lsp", "Starts an LSP server")
    }

    override fun launch(contextBuilder: Context.Builder) {
        contextBuilder.arguments("brj", args.toTypedArray())
        if (lsp) contextBuilder.option("brj.lsp", "true")
        val ctx = contextBuilder.build()

        ctx.initialize("brj")
        ctx.enter()

        try {
            if (!lsp && evalScripts.isEmpty()) {
                abort("Nothing to evaluate. See `brj --help` for options.")
            }

            evalScripts.forEach { script ->
                when (script) {
                    is EvalFile -> println(ctx.eval(Source.newBuilder("brj", script.file).build()))

                    is EvalString -> {
                        val result = if (script.str == "-") ctx.eval(
                            Source.newBuilder("brj", System.`in`.reader(), "<stdin>").build()
                        )
                        else ctx.eval("brj", script.str)

                        println(result)
                    }

                    is EvalMain -> {
                        val nsEnv = ctx.eval("brj", "(require! ${script.ns})").getMember(script.ns)
                        if (!nsEnv.canInvokeMember("main")) abort("Can't find 'main' function in ${script.ns}")
                        println(nsEnv.invokeMember("main", args))
                    }
                }
            }
        } finally {
            ctx.leave()
            ctx.close(false)
        }
    }

    override fun getLanguageId() = "brj"

    override fun preprocessArguments(
        arguments: MutableList<String>,
        polyglotOptions: MutableMap<String, String>
    ): List<String> {
        val unknownArgs = mutableListOf<String>()
        val iterator = arguments.listIterator()

        fun nextArg(opt: String) =
            if (iterator.hasNext()) iterator.next() else {
                abortInvalidArgument(opt, "Missing argument to $opt"); ""
            }

        loop@ while (iterator.hasNext()) {
            when (val arg = iterator.next()) {
                "-e", "--eval" -> evalScripts += EvalString(nextArg("--eval"))
                "-f", "--file" -> evalScripts += EvalFile(File(nextArg("--file")))
                "-m", "--main" -> evalScripts += EvalMain(nextArg("--main"))
                "--lsp" -> lsp = true
                "--" -> break@loop
                else -> unknownArgs.add(arg)
            }
        }

        args = arguments.subList(iterator.nextIndex(), arguments.size).toList()

        return unknownArgs
    }

    override fun collectArguments(options: MutableSet<String>) {
        options.addAll(
            setOf(
                "-e", "--eval",
                "-m", "--main",
                "-f", "--file",
                "--lsp"
            )
        )
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BridjeLauncher().launch(args)
        }
    }
}
