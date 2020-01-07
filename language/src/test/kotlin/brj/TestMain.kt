package brj

import brj.BridjeLanguage.Companion.currentBridjeContext
import brj.runtime.SymKind.ID
import brj.runtime.Symbol
import org.graalvm.polyglot.Context

fun <R> withCtx(f: (Context) -> R): R {
    val ctx = Context.newBuilder("brj").allowAllAccess(true).build()

    try {
        ctx.enter()
        return f(ctx)
    } finally {
        ctx.leave()
        ctx.close()
    }
}

fun main() {
    withCtx { ctx ->
        val foo = Symbol(ID, "main-foo")

        @Suppress("UNUSED_VARIABLE")
        val env = currentBridjeContext().require(foo)

        println(ctx.eval("brj", "[1 2]"))

        val value = ctx.eval("brj", """(main-foo/my-fn "Hello" "world!")""")
        println("value: $value")
    }
}
