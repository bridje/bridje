package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FnTest {
    @Test
    fun `create identity fn`() = withContext { ctx ->
        val result = ctx.evalBridje("fn: id(x) x")
        assertTrue(result.canExecute())
    }

    @Test
    fun `execute identity fn from Java`() = withContext { ctx ->
        val fn = ctx.evalBridje("fn: id(x) x")
        val result = fn.execute(42L)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `fn with multiple params`() = withContext { ctx ->
        val fn = ctx.evalBridje("fn: second(x, y) y")
        val result = fn.execute(1L, 2L)
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `fn returning vector`() = withContext { ctx ->
        val fn = ctx.evalBridje("fn: dup(x) [x x]")
        val result = fn.execute(5L)
        assertTrue(result.hasArrayElements())
        assertEquals(5L, result.getArrayElement(0).asLong())
        assertEquals(5L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `fn with no params`() = withContext { ctx ->
        val fn = ctx.evalBridje("fn: answer() 42")
        val result = fn.execute()
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `fn returning string`() = withContext { ctx ->
        val fn = ctx.evalBridje("fn: greet(x) \"hello\"")
        val result = fn.execute(1L)
        assertEquals("hello", result.asString())
    }
}
