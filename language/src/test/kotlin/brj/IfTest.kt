package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IfTest {
    private lateinit var ctx: Context

    @BeforeEach
    fun setUp() {
        ctx = Context.newBuilder()
            .logHandler(System.err)
            .build()
        ctx.enter()
    }

    @AfterEach
    fun tearDown() {
        ctx.leave()
        ctx.close()
    }

    private fun eval(code: String): Value = ctx.eval("bridje", code)

    @Test
    fun `if true returns then branch`() {
        val result = eval("if: true 1 2")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `if false returns else branch`() {
        val result = eval("if: false 1 2")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `if with expressions in branches`() {
        val result = eval("if: true [1 2] [3 4]")
        assertEquals(2, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `nested if`() {
        val result = eval("if: true (if false 1 2) 3")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `if in fn`() {
        val fn = eval("fn: choose(b) if: b 1 2")
        assertEquals(1L, fn.execute(true).asLong())
        assertEquals(2L, fn.execute(false).asLong())
    }

    @Test
    fun `non-boolean predicate throws - number`() {
        assertThrows(PolyglotException::class.java) {
            eval("if: 0 1 2")
        }
    }

    @Test
    fun `non-boolean predicate throws - string`() {
        assertThrows(PolyglotException::class.java) {
            eval("if: \"\" 1 2")
        }
    }

    @Test
    fun `non-boolean predicate throws - vector`() {
        assertThrows(PolyglotException::class.java) {
            eval("if: [] 1 2")
        }
    }

    @Test
    fun `boolean literals`() {
        assertTrue(eval("true").asBoolean())
        assertFalse(eval("false").asBoolean())
    }
}
