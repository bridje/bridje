package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class BuiltinsTest {
    @Test
    fun `add two integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(add 2 3)")
        assertEquals(5L, result.asLong())
    }

    @Test
    fun `add integer and double is a type error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("(add 2 3.5)")
        }
        assertTrue(ex.message?.contains("Cannot join") == true, "Expected type error, got: ${ex.message}")
    }

    @Test
    fun `subtract two integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(sub 10 3)")
        assertEquals(7L, result.asLong())
    }

    @Test
    fun `multiply two integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(mul 4 5)")
        assertEquals(20L, result.asLong())
    }

    @Test
    fun `divide two integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(div 20 4)")
        assertEquals(5L, result.asLong())
    }

    @Test
    fun `eq with equal integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(eq 5 5)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `eq with different integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(eq 5 3)")
        assertFalse(result.asBoolean())
    }

    @Test
    fun `neq with equal integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(neq 5 5)")
        assertFalse(result.asBoolean())
    }

    @Test
    fun `neq with different integers`() = withContext { ctx ->
        val result = ctx.evalBridje("(neq 5 3)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `lt with less than`() = withContext { ctx ->
        val result = ctx.evalBridje("(lt 3 5)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `lt with greater than`() = withContext { ctx ->
        val result = ctx.evalBridje("(lt 5 3)")
        assertFalse(result.asBoolean())
    }

    @Test
    fun `gt with greater than`() = withContext { ctx ->
        val result = ctx.evalBridje("(gt 5 3)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `gt with less than`() = withContext { ctx ->
        val result = ctx.evalBridje("(gt 3 5)")
        assertFalse(result.asBoolean())
    }

    @Test
    fun `lte with less than`() = withContext { ctx ->
        val result = ctx.evalBridje("(lte 3 5)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `lte with equal`() = withContext { ctx ->
        val result = ctx.evalBridje("(lte 5 5)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `lte with greater than`() = withContext { ctx ->
        val result = ctx.evalBridje("(lte 5 3)")
        assertFalse(result.asBoolean())
    }

    @Test
    fun `gte with greater than`() = withContext { ctx ->
        val result = ctx.evalBridje("(gte 5 3)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `gte with equal`() = withContext { ctx ->
        val result = ctx.evalBridje("(gte 5 5)")
        assertTrue(result.asBoolean())
    }

    @Test
    fun `gte with less than`() = withContext { ctx ->
        val result = ctx.evalBridje("(gte 3 5)")
        assertFalse(result.asBoolean())
    }

    @Test
    fun `add can be used in expressions`() = withContext { ctx ->
        val result = ctx.evalBridje("(add (add 1 2) 3)")
        assertEquals(6L, result.asLong())
    }

    @Test
    fun `mul with doubles`() = withContext { ctx ->
        val result = ctx.evalBridje("(mul 2.5 4.0)")
        assertEquals(10.0, result.asDouble())
    }

    @Test
    fun `div with doubles`() = withContext { ctx ->
        val result = ctx.evalBridje("(div 10.0 2.0)")
        assertEquals(5.0, result.asDouble())
    }

    @Test
    fun `division by zero throws error`() = withContext { ctx ->
        val ex = assertThrows(org.graalvm.polyglot.PolyglotException::class.java) {
            ctx.evalBridje("(div 10 0)")
        }
        assertTrue(ex.message?.contains("Division by zero") == true)
    }

    // Vector builtins

    @Test
    fun `count of vector`() = withContext { ctx ->
        assertEquals(3L, ctx.evalBridje("count([1, 2, 3])").asLong())
    }

    @Test
    fun `count of empty vector`() = withContext { ctx ->
        assertEquals(0L, ctx.evalBridje("count([])").asLong())
    }

    @Test
    fun `first of vector`() = withContext { ctx ->
        assertEquals(1L, ctx.evalBridje("first([1, 2, 3])").asLong())
    }

    @Test
    fun `first of empty vector throws`() = withContext { ctx ->
        assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("first([])")
        }
    }

    @Test
    fun `firstOrNull of vector`() = withContext { ctx ->
        assertEquals(1L, ctx.evalBridje("firstOrNull([1, 2, 3])").asLong())
    }

    @Test
    fun `firstOrNull of empty vector returns nil`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("firstOrNull([])").isNull)
    }

    @Test
    fun `rest of vector`() = withContext { ctx ->
        val result = ctx.evalBridje("rest([1, 2, 3])")
        assertTrue(result.hasArrayElements())
        assertEquals(2L, result.arraySize)
    }

    @Test
    fun `rest of empty vector`() = withContext { ctx ->
        val result = ctx.evalBridje("rest([])")
        assertTrue(result.hasArrayElements())
        assertEquals(0L, result.arraySize)
    }

    @Test
    fun `empty? of empty vector`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("empty?([])").asBoolean())
    }

    @Test
    fun `empty? of non-empty vector`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("empty?([1])").asBoolean())
    }

    @Test
    fun `cons prepends to vector`() = withContext { ctx ->
        val result = ctx.evalBridje("cons(0, [1, 2])")
        assertTrue(result.hasArrayElements())
        assertEquals(3L, result.arraySize)
        assertEquals(0L, result.getArrayElement(0).asLong())
        assertEquals(1L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `cons onto empty vector`() = withContext { ctx ->
        val result = ctx.evalBridje("cons(42, [])")
        assertTrue(result.hasArrayElements())
        assertEquals(1L, result.arraySize)
        assertEquals(42L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `concat two vectors`() = withContext { ctx ->
        val result = ctx.evalBridje("concat([1, 2], [3, 4])")
        assertTrue(result.hasArrayElements())
        assertEquals(4L, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(2L, result.getArrayElement(1).asLong())
        assertEquals(3L, result.getArrayElement(2).asLong())
        assertEquals(4L, result.getArrayElement(3).asLong())
    }

    @Test
    fun `concat with empty left`() = withContext { ctx ->
        val result = ctx.evalBridje("concat([], [1, 2])")
        assertTrue(result.hasArrayElements())
        assertEquals(2L, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `concat with empty right`() = withContext { ctx ->
        val result = ctx.evalBridje("concat([1, 2], [])")
        assertTrue(result.hasArrayElements())
        assertEquals(2L, result.arraySize)
        assertEquals(2L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `concat two empty vectors`() = withContext { ctx ->
        val result = ctx.evalBridje("concat([], [])")
        assertTrue(result.hasArrayElements())
        assertEquals(0L, result.arraySize)
    }

    @Test
    fun `println prints value and returns it`() {
        val output = ByteArrayOutputStream()
        Context.newBuilder()
            .allowAllAccess(true)
            .out(output)
            .build()
            .use { ctx ->
                ctx.enter()
                try {
                    val result = ctx.eval("bridje", "(println 42)")
                    assertEquals(42L, result.asLong())
                    assertEquals("42\n", output.toString())
                } finally {
                    ctx.leave()
                }
            }
    }
}
