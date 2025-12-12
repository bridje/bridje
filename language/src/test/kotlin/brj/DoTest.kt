package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DoTest {
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
    fun `explicit do returns last`() {
        val result = eval("(do 1 2 3)")
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `do with single expression`() {
        val result = eval("(do 42)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `do evaluates all expressions`() {
        // Returns the last expression [3], which is a vector with one element
        val result = eval("(do [1] [2] [3])")
        assertEquals(1, result.arraySize)
        assertEquals(3L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `do block syntax`() {
        val result = eval("""
            do:
              1
              2
              3
        """.trimIndent())
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `fn with multiple body expressions`() {
        val fn = eval("fn: f(x) 1 2 x")
        val result = fn.execute(42L)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `let with multiple body expressions`() {
        val result = eval("""
            let: [x 1]
              x
              x
              x
        """.trimIndent())
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `nested let with implicit do`() {
        val result = eval("""
            let: [x 1]
              let: [y 2]
                x
                y
        """.trimIndent())
        assertEquals(2L, result.asLong())
    }
}
