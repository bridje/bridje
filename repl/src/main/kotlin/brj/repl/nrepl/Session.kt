package brj.repl.nrepl

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

class Session(val id: String) : AutoCloseable {
    // allowAllAccess required for Bridje language; has security implications
    private val context: Context = Context.newBuilder("bridje")
        .allowAllAccess(true)
        .out(System.out)
        .err(System.err)
        .build()

    sealed class EvalResult {
        data class Success(val value: String) : EvalResult()
        data class Error(val exception: Exception) : EvalResult()
    }

    private inline fun <R> Context.inContext(f: Context.() -> R): R {
        enter()
        return try {
            f()
        } finally {
            leave()
        }
    }

    fun eval(code: String): EvalResult = context.inContext {
        try {
            val source = Source.newBuilder("bridje", code, "<nrepl>").build()
            EvalResult.Success(eval(source).toString())
        } catch (e: Exception) {
            EvalResult.Error(e)
        }
    }

    override fun close() = context.close()
}
