package brj

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BridjeLanguageTest {
    @Test
    fun testBridjeLanguage() = withContext { ctx ->
        assertEquals(42.4, ctx.evalBridje("42.4").asDouble())
    }
}