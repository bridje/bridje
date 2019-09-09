package brj

import brj.emitter.Symbol
import brj.reader.NSForms.Loader.Companion.ClasspathLoader
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.Context

fun main() {
    val ctx = Context.newBuilder("bridje").allowAllAccess(true).build()

    try {
        ctx.enter()

        val foo = Symbol.mkSym("foo")
        val bar = Symbol.mkSym("bar")

        val fooSource = Source.newBuilder("bridje", BridjeLanguage::class.java.getResource("main-foo.brj").readText(), "foo").build()
        val barSource = Source.newBuilder("bridje", BridjeLanguage::class.java.getResource("main-bar.brj").readText(), "bar").build()

        // TODO
        val env = BridjeLanguage.require(setOf(foo), ClasspathLoader(sources = mapOf(foo to fooSource, bar to barSource)))

        val value = ctx.eval("bridje", """(str "Hello" " " "world!")""")

        println("value: $value")
    } finally {
        ctx.leave()
        ctx.close(true)
    }

}
