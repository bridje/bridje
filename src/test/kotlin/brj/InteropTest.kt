package brj

import brj.runtime.SymKind
import brj.runtime.SymKind.*
import brj.runtime.Symbol
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("UNUSED")
fun reverse(str: String) = str.reversed()

class InteropTest {
    @Test
    fun `e2e interop test`() {
        withCtx { ctx ->
            BridjeLanguage.require(setOf(Symbol(ID, "brj.interop-test")))
            assertEquals("hello world", ctx.eval("brj", """(brj.interop-test/str-reverse "dlrow olleh")""").asString())
        }
    }
}