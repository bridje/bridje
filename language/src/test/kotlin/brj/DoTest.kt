package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DoTest {
    @Test
    fun `explicit do returns last`() = withContext { ctx ->
        val result = ctx.evalBridje("(do 1 2 3)")
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `do with single expression`() = withContext { ctx ->
        val result = ctx.evalBridje("(do 42)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `do evaluates all expressions`() = withContext { ctx ->
        // Returns the last expression [3], which is a vector with one element
        val result = ctx.evalBridje("(do [1] [2] [3])")
        assertEquals(1, result.arraySize)
        assertEquals(3L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `do block syntax`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              1
              2
              3
        """.trimIndent())
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `fn with multiple body expressions`() = withContext { ctx ->
        val fn = ctx.evalBridje("fn: f(x) 1 2 x")
        val result = fn.execute(42L)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `let with multiple body expressions`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            let: [x 1]
              x
              x
              x
        """.trimIndent())
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `nested let with implicit do`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            let: [x 1]
              let: [y 2]
                x
                y
        """.trimIndent())
        assertEquals(2L, result.asLong())
    }
}
