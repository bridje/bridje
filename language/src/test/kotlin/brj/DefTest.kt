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
        val result = eval("""
            do:
              def: x 42
              x
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `def function`() {
        val result = eval("""
            do:
              def: id(x) x
              id(42)
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `multiple defs`() {
        val result = eval("""
            do:
              def: a 10
              def: b 20
              b
        """.trimIndent())
        assertEquals(20L, result.asLong())
    }

    @Test
    fun `def then use`() {
        val result = eval("""
            do:
              def: a 10
              a
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `def fn calling another def`() {
        val result = eval("""
            do:
              def: id(x) x
              def: wrap(x) id(x)
              wrap(42)
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }
}
