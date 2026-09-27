package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IfLetTest {
    @Test
    fun `ifLet with non-nil value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ifLet: [x 42]
              x
              0
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `ifLet with nil value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ifLet: [x nil]
              x
              99
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `ifLet binds value in then branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ifLet: [x 21]
              add(x, x)
              0
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `ifLet binding is not in scope in the else branch`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                ifLet: [x nil]
                  0
                  x
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("x") == true, "Expected unresolved x, got: ${ex.message}")
    }

    @Test
    fun `ifLet requires a name and a value`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("ifLet([x], 1, 0)")
        }
        assertTrue(ex.message?.contains("binding vector") == true, "Got: ${ex.message}")
    }
}
