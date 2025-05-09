package brj

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BridjeLanguageTest {
    @Test
    fun testBridjeLanguage() {
        Context.newBuilder()
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    assertEquals("bell", ctx.eval("brj", "42.4").asDouble())
                } finally {
                    ctx.leave()
                }
            }
    }
}