package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FnTest {
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
    fun `create identity fn`() {
        val result = eval("fn: id(x) x")
        assertTrue(result.canExecute())
    }

    @Test
    fun `execute identity fn from Java`() {
        val fn = eval("fn: id(x) x")
        val result = fn.execute(42L)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `fn with multiple params`() {
        val fn = eval("fn: second(x, y) y")
        val result = fn.execute(1L, 2L)
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `fn returning vector`() {
        val fn = eval("fn: dup(x) [x x]")
        val result = fn.execute(5L)
        assertTrue(result.hasArrayElements())
        assertEquals(5L, result.getArrayElement(0).asLong())
        assertEquals(5L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `fn with no params`() {
        val fn = eval("fn: answer() 42")
        val result = fn.execute()
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `fn returning string`() {
        val fn = eval("fn: greet(x) \"hello\"")
        val result = fn.execute(1L)
        assertEquals("hello", result.asString())
    }
}
