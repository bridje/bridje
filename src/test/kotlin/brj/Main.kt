package brj

import brj.reader.NSForms.Loader.Companion.ClasspathLoader
import brj.runtime.Symbol.Companion.mkSym
import com.oracle.truffle.api.source.Source
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
        val foo = mkSym("foo")
        val bar = mkSym("bar")

        val fooSource = Source.newBuilder("brj", BridjeLanguage::class.java.getResource("main-foo.brj").readText(), "foo").build()
        val barSource = Source.newBuilder("brj", BridjeLanguage::class.java.getResource("main-bar.brj").readText(), "bar").build()

        @Suppress("UNUSED_VARIABLE")
        val env = BridjeLanguage.require(setOf(foo), ClasspathLoader(sources = mapOf(foo to fooSource, bar to barSource)))

        println(ctx.eval("brj", "[1 2]"))

        val value = ctx.eval("brj", """(foo/my-fn "Hello" "world!")""")
        println("value: $value")
    }
}
