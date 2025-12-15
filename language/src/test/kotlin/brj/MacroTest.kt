package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MacroTest {
    @Test
    fun `simple macro expansion`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: unless(cond, body)
                List([Symbol("if") cond 'nil body])
              unless: false
                42
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `macro receives unevaluated forms`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: when(cond, body)
                List([Symbol("if") cond body 'nil])
              when: true
                1
        """)
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `when macro returns nil for false condition`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: when(cond, body)
                List([Symbol("if") cond body 'nil])
              when: false
                1
        """)
        assertTrue(result.isNull)
    }

    @Test
    fun `macro with multiple arguments`() = withContext { ctx ->
        val result1 = ctx.evalBridje("""
            do:
              defmacro: if-not(cond, then, else)
                List([Symbol("if") cond else then])
              if-not: false
                1
                2
        """)
        assertEquals(1L, result1.asLong())
    }

    @Test
    fun `macro can return argument form`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: identity-macro(x)
                x
              identity-macro(42)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `macro expands before evaluation`() = withContext { ctx ->
        // This test verifies that macro args are forms, not evaluated values
        val result = ctx.evalBridje("""
            do:
              def: x 10
              defmacro: get-form(f)
                f
              get-form(x)
        """)
        // The macro returns the form 'x, which then evaluates to 10
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `macro with unquote`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: unless(cond, body)
                '(if ~cond nil ~body)
              unless: false
                42
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `macro with unquote - when macro`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: when(cond, body)
                '(if ~cond ~body nil)
              when: true
                1
        """)
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `macro with unquote returns nil for false condition`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: when(cond, body)
                '(if ~cond ~body nil)
              when: false
                1
        """)
        assertTrue(result.isNull)
    }

    @Test
    fun `macro with multiple unquotes`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: if-not(cond, then, else)
                '(if ~cond ~else ~then)
              if-not: false
                1
                2
        """)
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `error on unquote outside quote`() = withContext { ctx ->
        assertThrows(RuntimeException::class.java) {
            ctx.evalBridje("~42")
        }
    }

    @Test
    fun `anonymous function macro simple`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: increment #: add(it, 1)
              increment(5)
        """)
        assertEquals(6L, result.asLong())
    }

    @Test
    fun `anonymous function macro with multiplication`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: double #: mul(it, 2)
              double(7)
        """)
        assertEquals(14L, result.asLong())
    }

    @Test
    fun `anonymous function macro in call`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: apply(f, x) f(x)
              apply(#: add(it, 10), 5)
        """)
        assertEquals(15L, result.asLong())
    }

    @Test
    fun `anonymous function macro with comparison`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: isPositive #: gt(it, 0)
              isPositive(5)
        """)
        assertEquals(true, result.asBoolean())
    }
}
