package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DefTest {
    @Test
    fun `def value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: x 42
              x
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `def function`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: id(x) x
              id(42)
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `multiple defs`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: a 10
              def: b 20
              b
        """.trimIndent())
        assertEquals(20L, result.asLong())
    }

    @Test
    fun `def then use`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: a 10
              a
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `def fn calling another def`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: id(x) x
              def: wrap(x) id(x)
              wrap(42)
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }
}
