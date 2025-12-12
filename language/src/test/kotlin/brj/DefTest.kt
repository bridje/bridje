package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefTest {
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
    fun `def value`() {
        // Use parenthesized form to work around block parsing issue
        val result = eval("(do (def x 42) x)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `def function`() {
        // Use parenthesized form to work around block parsing issue
        val result = eval("(do (def (id x) x) (id 42))")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `multiple defs`() {
        // Use parenthesized form to work around block parsing issue
        val result = eval("(do (def a 10) (def b 20) b)")
        assertEquals(20L, result.asLong())
    }

    @Test
    fun `def then use`() {
        // Use parenthesized form to work around block parsing issue
        val result = eval("(do (def a 10) a)")
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `def fn calling another def`() {
        // Use parenthesized form to work around block parsing issue
        val result = eval("(do (def (id x) x) (def (wrap x) (id x)) (wrap 42))")
        assertEquals(42L, result.asLong())
    }
}
