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
    fun `gensym returns unique symbols`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: a gensym()
              def: b gensym()
              eq(a, b)
        """)
        assertFalse(result.asBoolean(), "gensym should return unique symbols")
    }

    @Test
    fun `gensym with prefix`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            gensym("foo")
        """)
        val str = result.toString()
        assertTrue(str.startsWith("foo__"), "gensym with prefix should start with 'foo__', got: $str")
    }

    @Test
    fun `foo# in syntax-quote resolves to same gensym within form`() = withContext { ctx ->
        // The macro uses tmp# twice - both should resolve to the same gensym
        val result = ctx.evalBridje("""
            do:
              defmacro: dup(x)
                '(let [tmp# ~x] (add tmp# tmp#))
              dup(21)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `foo# avoids variable capture`() = withContext { ctx ->
        // Without gensym, this would have a variable capture bug
        val result = ctx.evalBridje("""
            do:
              defmacro: dup(x)
                '(let [tmp# ~x] (add tmp# tmp#))
              let: [tmp 100]
                dup(21)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `different foo# names get different gensyms`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: swap-add(x, y)
                '(let [a# ~x b# ~y] (add b# a#))
              swap-add(10, 32)
        """)
        assertEquals(42L, result.asLong())
    }
}
