package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SymbolTest {

    @Test
    fun `Symbol from Bridje constructs a Symbol value`() = withContext { ctx ->
        val result = ctx.evalBridje("Symbol(\"foo\")")
        assertEquals("Symbol", result.metaObject.metaSimpleName)
        assertEquals("foo", result.toString())
    }

    @Test
    fun `Symbol destructures as tagged tuple with name element`() = withContext { ctx ->
        val result = ctx.evalBridje("Symbol(\"foo\")")
        assertTrue(result.hasArrayElements(), "Symbol should expose array elements")
        assertEquals(1L, result.arraySize)
        assertEquals("foo", result.getArrayElement(0).asString())
    }
}
