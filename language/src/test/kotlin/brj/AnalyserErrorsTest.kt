package brj

import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnalyserErrorsTest {
    private fun PolyglotException.errors(): Value {
        assertTrue(isGuestException, "Expected guest exception")
        val guest = guestObject!!
        assertTrue(guest.hasArrayElements(), "Expected errors to be an array")
        return guest
    }

    private fun Value.toAnalyserError(): Pair<String, Int> {
        val ex = assertThrows(PolyglotException::class.java) { throwException() }
        return Pair(ex.message!!, ex.sourceLocation.startLine)
    }

    @Test
    fun `collects multiple errors`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  unknownSymbol1
                  unknownSymbol2
                  unknownSymbol3
            """.trimIndent())
        }

        val errors = ex.errors()
        assertEquals(3, errors.arraySize)

        val (msg0, line0) = errors.getArrayElement(0).toAnalyserError()
        assertTrue(msg0.contains("unknownSymbol1"))
        assertEquals(2, line0)

        val (msg1, line1) = errors.getArrayElement(1).toAnalyserError()
        assertTrue(msg1.contains("unknownSymbol2"))
        assertEquals(3, line1)

        val (msg2, line2) = errors.getArrayElement(2).toAnalyserError()
        assertTrue(msg2.contains("unknownSymbol3"))
        assertEquals(4, line2)
    }

    @Test
    fun `errors in function bodies propagate`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  def: foo(x)
                    badSymbol
                  anotherBadSymbol
            """.trimIndent())
        }

        val errors = ex.errors()
        assertEquals(2, errors.arraySize)

        val (msg0, line0) = errors.getArrayElement(0).toAnalyserError()
        assertTrue(msg0.contains("badSymbol"))
        assertEquals(3, line0)

        val (msg1, line1) = errors.getArrayElement(1).toAnalyserError()
        assertTrue(msg1.contains("anotherBadSymbol"))
        assertEquals(4, line1)
    }

    @Test
    fun `error messages include location`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("unknownSymbol")
        }

        val errors = ex.errors()
        assertEquals(1, errors.arraySize)

        val (msg, line) = errors.getArrayElement(0).toAnalyserError()
        assertTrue(msg.contains("unknownSymbol"))
        assertEquals(1, line)
    }
}
