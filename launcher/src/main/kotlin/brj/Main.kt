package brj

import brj.LauncherArgs.EvalOpts.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import java.io.File

class LauncherArgs : CliktCommand(name = "brj") {
    sealed class EvalOpts {
        data class EvalFile(val file: File) : EvalOpts()
        data class EvalString(val str: String) : EvalOpts()
        data class EvalMain(val ns: String) : EvalOpts()
    }

    val evalOpts by mutuallyExclusiveOptions<EvalOpts>(
        option("-m", "--main-ns").convert { EvalMain(it) },
        option("-e", "--eval").convert { EvalString(it) },
        option("-f", "--file").convert {
            EvalFile(File(it).also { file ->
                if (!file.exists()) fail("\"$it\" does not exist.")
                if (file.isDirectory) fail("\"$it\" is a directory.")
                if (!file.canRead()) fail("\"$it\" is not readable.")
            })
        }
    )

    val args by argument().multiple()

    override fun run() {
        val ctx = Context.newBuilder().allowAllAccess(true).build()

        ctx.enter()

        try {
            when (val opts = evalOpts) {
                null -> ctx.eval(Source.newBuilder("brj", System.`in`.reader(), "<stdin>").build())
                is EvalFile -> ctx.eval(Source.newBuilder("brj", opts.file).build())
                is EvalString -> ctx.eval(Source.newBuilder("brj", opts.str, "").build())
                is EvalMain -> {
                    val main = ctx.eval("brj", "(require! ${opts.ns}) ${opts.ns}/-main")
                    assert(main.canExecute())
                    main.execute(args)
                }
            }.also { println(it) }
        } finally {
            ctx.leave()
            ctx.close(false)
        }
    }
}

fun main(args: Array<String>) {
    LauncherArgs().main(args)
}