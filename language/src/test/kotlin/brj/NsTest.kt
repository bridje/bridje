package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NsTest {
    @Test
    fun `ns defines namespace`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: foo
            def: x 42
        """.trimIndent())
        val result = ctx.evalBridje("foo/x")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `ns with multiple defs`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: foo
            def: a 1
            def: b 2
        """.trimIndent())
        assertEquals(1L, ctx.evalBridje("foo/a").asLong())
        assertEquals(2L, ctx.evalBridje("foo/b").asLong())
    }

    @Test
    fun `ns with function`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: math
            def: double(x) add(x, x)
        """.trimIndent())
        val result = ctx.evalBridje("math/double(21)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `qualified reference to core`() = withContext { ctx ->
        val result = ctx.evalBridje("brj:core/add(1, 2)")
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `ns with nested name`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: my:lib
            def: val 99
        """.trimIndent())
        val result = ctx.evalBridje("my:lib/val")
        assertEquals(99L, result.asLong())
    }
}
