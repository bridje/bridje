package brj

import brj.runtime.Symbol
import org.junit.jupiter.api.Test

fun println(str: String): String {
    kotlin.io.println(str)
    return str
}

internal class FxTest {
    @Test
    fun `e2e fx test`() {
        withCtx {ctx ->
            BridjeLanguage.require(setOf(Symbol.mkSym("brj.fx-test")))
            ctx.eval("brj", """(brj.fx-test/straight-println "Hello")""")
            ctx.eval("brj", """(brj.fx-test/closure-println "Hello")""")
        }
    }
}