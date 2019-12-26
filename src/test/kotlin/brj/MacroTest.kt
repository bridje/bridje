package brj

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class MacroTest {
    @Test
    fun `str varargs`() {
        withCtx { ctx ->
            assertEquals("Hello World", ctx.eval("brj", """(str "Hello" " " "World")""").asString())
        }
    }
}