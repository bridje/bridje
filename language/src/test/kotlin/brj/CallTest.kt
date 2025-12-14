package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CallTest {
    @Test
    fun `inline call identity`() = withContext { ctx ->
        val result = ctx.evalBridje("((fn (id x) x) 42)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `inline call with multiple args`() = withContext { ctx ->
        val result = ctx.evalBridje("((fn (second x y) y) 1 2)")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `call fn via let binding`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            let: [f fn: id(x) x]
              f(99)
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `nested calls`() = withContext { ctx ->
        val result = ctx.evalBridje("((fn (outer x) ((fn (inner y) y) x)) 7)")
        assertEquals(7L, result.asLong())
    }

    @Test
    fun `call non-callable throws`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("(1 2 3)")
        }
        assertTrue(ex.message?.contains("Not callable") == true, "Expected 'Not callable' error, got: ${ex.message}")
    }

    @Test
    fun `call with no args`() = withContext { ctx ->
        val result = ctx.evalBridje("((fn (answer) 42))")
        assertEquals(42L, result.asLong())
    }

    // Closures not yet implemented - this test would require fn to capture outer scope
    // @Test
    // fun `higher order - fn returning fn`() = withContext { ctx ->
    //     val result = ctx.evalBridje("(((fn (outer x) (fn (inner y) x)) 1) 2)")
    //     assertEquals(1L, result.asLong())
    // }

    @Test
    fun `call fn that returns vector`() = withContext { ctx ->
        val result = ctx.evalBridje("((fn (pair x y) [x y]) 1 2)")
        assertTrue(result.hasArrayElements())
        assertEquals(2, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(2L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `deeply nested inline calls`() = withContext { ctx ->
        val result = ctx.evalBridje("((fn (a x) ((fn (b y) ((fn (c z) z) y)) x)) 42)")
        assertEquals(42L, result.asLong())
    }
}
