package brj

import brj.BridjeLanguage.Companion.currentBridjeContext
import brj.runtime.SymKind.ID
import brj.runtime.Symbol
import org.junit.jupiter.api.Test

fun println(str: String): String {
    kotlin.io.println(str)
    return str
}

internal class FxTest {
    @Test
    fun `e2e fx test`() {
        withCtx { ctx ->
            currentBridjeContext().require(Symbol(ID, "brj.fx-test"))
            ctx.eval("brj", """(brj.fx-test/straight-println "Hello")""")
            ctx.eval("brj", """(brj.fx-test/intercepted "Hello")""")
            ctx.eval("brj", """(brj.fx-test/closure-println "Hello")""")
        }
    }
}