package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LetTest {
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
    fun `single binding`() {
        val result = eval("(let [x 1] x)")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `multiple bindings`() {
        val result = eval("(let [x 1 y 2] y)")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `sequential reference - later binding can reference earlier`() {
        val result = eval("(let [x 1 y x] y)")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `nested let - inner accesses outer`() {
        val result = eval("(let [x 1] (let [y 2] x))")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `nested let - shadow outer binding`() {
        val result = eval("(let [x 1] (let [x 2] x))")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `expression in binding`() {
        val result = eval("(let [x [1 2 3]] x)")
        assertTrue(result.hasArrayElements())
        assertEquals(3, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `multiple bindings with vectors`() {
        val result = eval("(let [a [1 2] b [3 4]] b)")
        assertTrue(result.hasArrayElements())
        assertEquals(2, result.arraySize)
        assertEquals(3L, result.getArrayElement(0).asLong())
        assertEquals(4L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `unknown symbol throws error`() {
        val ex = assertThrows(PolyglotException::class.java) {
            eval("(let [x 1] y)")
        }
        assertTrue(ex.message?.contains("Unknown symbol: y") == true, "Expected error about unknown symbol, got: ${ex.message}")
    }

    @Test
    fun `unknown symbol at top level`() {
        val ex = assertThrows(PolyglotException::class.java) {
            eval("x")
        }
        assertTrue(ex.message?.contains("Unknown symbol: x") == true, "Expected error about unknown symbol, got: ${ex.message}")
    }

    @Test
    fun `shadowing within same let`() {
        // (let [x 1 x 2] x) - second x shadows first
        val result = eval("(let [x 1 x 2] x)")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `shadowing - outer still accessible before shadow`() {
        // The first x=1, then y references x (still 1), then x gets shadowed to 2
        val result = eval("(let [x 1 y x x 2] y)")
        assertEquals(1L, result.asLong())
    }
}
