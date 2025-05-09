package brj

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BridjeLanguageTest {
    @Test
    fun testBridjeLanguage() {
        Context.create().use { ctx ->
            try {
                ctx.enter()
                assertEquals(42, ctx.eval("brj", "42").asLong())
            } finally {
                ctx.leave()
            }
        }
    }
}