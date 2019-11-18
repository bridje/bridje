package brj

import brj.runtime.Symbol.Companion.mkSym
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("UNUSED")
fun reverse(str: String) = str.reversed()

class InteropTest {
    @Test
    fun `e2e interop test`() {
        withCtx { ctx ->
            BridjeLanguage.require(setOf(mkSym("brj.interop-test")))
            assertEquals("hello world", ctx.eval("brj", """(brj.interop-test/str-reverse "dlrow olleh")""").asString())
        }
    }
}