package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IfTest {
    @Test
    fun `if true returns then branch`() = withContext { ctx ->
        val result = ctx.evalBridje("if: true 1 2")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `if false returns else branch`() = withContext { ctx ->
        val result = ctx.evalBridje("if: false 1 2")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `if with expressions in branches`() = withContext { ctx ->
        val result = ctx.evalBridje("if: true [1 2] [3 4]")
        assertEquals(2, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `nested if`() = withContext { ctx ->
        val result = ctx.evalBridje("if: true (if false 1 2) 3")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `if in fn`() = withContext { ctx ->
        val fn = ctx.evalBridje("fn: choose(b) if: b 1 2")
        assertEquals(1L, fn.execute(true).asLong())
        assertEquals(2L, fn.execute(false).asLong())
    }

    @Test
    fun `non-boolean predicate throws - number`() = withContext { ctx ->
        assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("if: 0 1 2")
        }
    }

    @Test
    fun `non-boolean predicate throws - string`() = withContext { ctx ->
        assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("if: \"\" 1 2")
        }
    }

    @Test
    fun `non-boolean predicate throws - vector`() = withContext { ctx ->
        assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("if: [] 1 2")
        }
    }

    @Test
    fun `boolean literals`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("true").asBoolean())
        assertFalse(ctx.evalBridje("false").asBoolean())
    }
}
