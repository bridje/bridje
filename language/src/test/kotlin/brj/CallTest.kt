package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CallTest {
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
    fun `inline call identity`() {
        val result = eval("((fn (id x) x) 42)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `inline call with multiple args`() {
        val result = eval("((fn (second x y) y) 1 2)")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `call fn via let binding`() {
        val result = eval("""
            let: [f fn: id(x) x]
              f(99)
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `nested calls`() {
        val result = eval("((fn (outer x) ((fn (inner y) y) x)) 7)")
        assertEquals(7L, result.asLong())
    }

    @Test
    fun `call non-callable throws`() {
        val ex = assertThrows(PolyglotException::class.java) {
            eval("(1 2 3)")
        }
        assertTrue(ex.message?.contains("Not callable") == true, "Expected 'Not callable' error, got: ${ex.message}")
    }

    @Test
    fun `call with no args`() {
        val result = eval("((fn (answer) 42))")
        assertEquals(42L, result.asLong())
    }

    // Closures not yet implemented - this test would require fn to capture outer scope
    // @Test
    // fun `higher order - fn returning fn`() {
    //     val result = eval("(((fn (outer x) (fn (inner y) x)) 1) 2)")
    //     assertEquals(1L, result.asLong())
    // }

    @Test
    fun `call fn that returns vector`() {
        val result = eval("((fn (pair x y) [x y]) 1 2)")
        assertTrue(result.hasArrayElements())
        assertEquals(2, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(2L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `deeply nested inline calls`() {
        val result = eval("((fn (a x) ((fn (b y) ((fn (c z) z) y)) x)) 42)")
        assertEquals(42L, result.asLong())
    }
}
