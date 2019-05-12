package brj

import brj.BridjeLanguage.Companion.require
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main() {
    val ctx = Context.newBuilder("bridje").allowAllAccess(true).build()

    try {
        ctx.enter()

        val foo = Symbol.mkSym("foo")
        val bar = Symbol.mkSym("bar")

        val fooSource = Source.create("bridje", BridjeLanguage::class.java.getResource("main-foo.brj").readText())

        val barSource = Source.create("bridje", BridjeLanguage::class.java.getResource("main-bar.brj").readText())

        val env = require(setOf(foo), BridjeNSLoader(sources = mapOf(foo to fooSource, bar to barSource)))

        val value = ctx.eval("bridje", """(str "Hello" " " "world!")""")

        println("value: $value")
    } finally {
        ctx.leave()
        ctx.close(true)
    }

}
