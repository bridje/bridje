package brj

import brj.BridjeLanguage.Companion.currentBridjeContext
import brj.runtime.SymKind.ID
import brj.runtime.Symbol
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("UNUSED")
fun reverse(str: String) = str.reversed()

class InteropTest {
    @Test
    fun `e2e interop test`() {
        withCtx { ctx ->
            currentBridjeContext().require(Symbol(ID, "brj.interop-test"))
            assertEquals("hello world", ctx.eval("brj", """(brj.interop-test/str-reverse "dlrow olleh")""").asString())
        }
    }
}