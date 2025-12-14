package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NilTest {
    @Test
    fun `nil is null`() = withContext { ctx ->
        val result = ctx.evalBridje("nil")
        assertTrue(result.isNull)
    }

    @Test
    fun `nil display string`() = withContext { ctx ->
        val result = ctx.evalBridje("nil")
        assertEquals("nil", result.toString())
    }
}
