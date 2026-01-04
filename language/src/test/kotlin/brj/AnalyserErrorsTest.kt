package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnalyserErrorsTest {
    private fun PolyglotException.errorCount(): Int {
        assertTrue(isGuestException, "Expected guest exception")
        val guest = guestObject!!
        val errors = guest.getMember("errors")
        return errors.arraySize.toInt()
    }

    private fun PolyglotException.errorMessages(): List<String> {
        val guest = guestObject!!
        val errors = guest.getMember("errors")
        return (0 until errors.arraySize).map { i ->
            errors.getArrayElement(i).getMember("message").asString()
        }
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

        assertEquals(3, ex.errorCount())
        assertTrue(ex.errorMessages().all { it.contains("Unknown symbol") })
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

        assertEquals(2, ex.errorCount())
        val messages = ex.errorMessages()
        assertTrue(messages.any { it.contains("badSymbol") })
        assertTrue(messages.any { it.contains("anotherBadSymbol") })
    }

    @Test
    fun `error messages include location`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("unknownSymbol")
        }

        assertEquals(1, ex.errorCount())
        // Location is in the exception's source location
        assertTrue(ex.sourceLocation != null)
    }
}
