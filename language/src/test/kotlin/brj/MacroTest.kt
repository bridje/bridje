package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MacroTest {
    @Test
    fun `simple macro expansion`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.macros
              require:
                brj: as(forms, f)

            defmacro: unless(cond, body)
              f/List([f/SymbolForm(Symbol("if")) cond 'nil body])

            def: __result unless(false, 42)
        """.trimIndent()).getMember("__result")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `macro receives unevaluated forms`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.macros
              require:
                brj: as(forms, f)

            defmacro: when(cond, body)
              f/List([f/SymbolForm(Symbol("if")) cond body 'nil])

            def: __result when(true, 1)
        """.trimIndent()).getMember("__result")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `when macro returns nil for false condition`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.macros
              require:
                brj: as(forms, f)

            defmacro: when(cond, body)
              f/List([f/SymbolForm(Symbol("if")) cond body 'nil])

            def: __result when(false, 1)
        """.trimIndent()).getMember("__result")
        assertTrue(result.isNull)
    }

    @Test
    fun `macro with multiple arguments`() = withContext { ctx ->
        val result1 = ctx.evalBridje("""
            ns: test.macros
              require:
                brj: as(forms, f)

            defmacro: if-not(cond, then, else)
              f/List([f/SymbolForm(Symbol("if")) cond else then])

            def: __result if-not(false, 1, 2)
        """.trimIndent()).getMember("__result")
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
    fun `error on unquote-splice outside quote`() = withContext { ctx ->
        assertThrows(RuntimeException::class.java) {
            ctx.evalBridje("~@[1 2 3]")
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

    @Test
    fun `ifLet with non-nil value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ifLet: [x 42]
              x
              0
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `ifLet with nil value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ifLet: [x nil]
              x
              99
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `unlessLet with nil value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            unlessLet: [x nil]
              99
              x
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `unlessLet with non-nil value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            unlessLet: [x 42]
              0
              x
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `ifLet binds value in then branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ifLet: [x 21]
              add(x, x)
              0
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `orElse returns value when non-nil`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            orElse(42, 99)
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `orElse returns default when nil`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            orElse(nil, 99)
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `orElse does not evaluate default when value is non-nil`() = withContext { ctx ->
        // If default were evaluated, this would cause a division by zero
        val result = ctx.evalBridje("""
            orElse(42, div(1, 0))
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `orElse chains multiple defaults`() = withContext { ctx ->
        val result = ctx.evalBridje("orElse(nil, nil, 42)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `orElse chain short-circuits`() = withContext { ctx ->
        val result = ctx.evalBridje("orElse(nil, 42, div(1, 0))")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `variadic macro with rest-only param`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: always42(& forms)
                '42
              always42(1, 2, 3)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `variadic macro with rest-only param and zero args`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: always42(& forms)
                '42
              always42()
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `variadic macro with fixed and rest params`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: returnFirst(x, & rest)
                x
              returnFirst(42, 1, 2, 3)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `variadic macro with empty rest`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: returnFirst(x, & rest)
                x
              returnFirst(42)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `variadic macro can access rest elements`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: secondArg(x, & rest)
                first(rest)
              secondArg(1, 42, 3)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `variadic macro can count rest elements`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.macros
              require:
                brj: as(forms, f)

            defmacro: countRest(& rest)
              f/Int(count(rest))

            def: __result countRest(1, 2, 3)
        """.trimIndent()).getMember("__result")
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `variadic macro empty rest has zero count`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.macros
              require:
                brj: as(forms, f)

            defmacro: countRest(& rest)
              f/Int(count(rest))

            def: __result countRest()
        """.trimIndent()).getMember("__result")
        assertEquals(0L, result.asLong())
    }

    // Threading macro

    @Test
    fun `thread with no steps returns seed`() = withContext { ctx ->
        val result = ctx.evalBridje("->(42)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `thread with bare symbol`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: inc(x) add(x, 1)
              ->(41, inc)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `thread with call form inserts as first arg`() = withContext { ctx ->
        val result = ctx.evalBridje("->(10, add(32))")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `thread with multiple steps`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: inc(x) add(x, 1)
              ->(10, add(31), inc)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `thread with colon block syntax`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: inc(x) add(x, 1)
              ->: 10 add(31) inc()
        """)
        assertEquals(42L, result.asLong())
    }

    // and/or macros

    @Test
    fun `and with no args returns true`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("and()").asBoolean())
    }

    @Test
    fun `and with single true arg`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("and(true)").asBoolean())
    }

    @Test
    fun `and with single false arg`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("and(false)").asBoolean())
    }

    @Test
    fun `and with all true`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("and(true, true, true)").asBoolean())
    }

    @Test
    fun `and short-circuits on false`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("and(true, false, eq(div(1, 0), 0))").asBoolean())
    }

    @Test
    fun `or with no args returns false`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("or()").asBoolean())
    }

    @Test
    fun `or with single false arg`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("or(false)").asBoolean())
    }

    @Test
    fun `or with all false`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("or(false, false, false)").asBoolean())
    }

    @Test
    fun `or returns true on first true`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("or(false, true, false)").asBoolean())
    }

    @Test
    fun `or short-circuits on true`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("or(false, true, eq(div(1, 0), 0))").asBoolean())
    }

    // when/unless

    @Test
    fun `when true evaluates body`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("when(true, 42)").asLong())
    }

    @Test
    fun `when false returns nil`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("when(false, 42)").isNull)
    }

    @Test
    fun `when with multiple body forms`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("when(true, println(1), 42)").asLong())
    }

    @Test
    fun `unless false evaluates body`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("unless(false, 42)").asLong())
    }

    @Test
    fun `unless true returns nil`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("unless(true, 42)").isNull)
    }

    // cond

    @Test
    fun `cond matches first true test`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("cond(false, 1, true, 42, 99)").asLong())
    }

    @Test
    fun `cond falls through to default`() = withContext { ctx ->
        assertEquals(99L, ctx.evalBridje("cond(false, 1, false, 2, 99)").asLong())
    }

    @Test
    fun `cond with no default returns nil`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("cond(false, 1)").isNull)
    }

    @Test
    fun `cond short-circuits`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("cond(true, 42, eq(div(1, 0), 0), 99)").asLong())
    }

    // cond->

    @Test
    fun `cond-then applies matching steps`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("cond->(10, true, add(32), false, sub(1))").asLong())
    }

    @Test
    fun `cond-then skips non-matching steps`() = withContext { ctx ->
        assertEquals(10L, ctx.evalBridje("cond->(10, false, add(32))").asLong())
    }

    @Test
    fun `cond-then threads through multiple matching steps`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("cond->(10, true, add(31), true, add(1), false, sub(100))").asLong())
    }

    @Test
    fun `cond-then with no clauses returns seed`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("cond->(42)").asLong())
    }

    // doto

    @Test
    fun `doto threads and returns value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: x [1, 2, 3]
              doto(x, count(), count())
        """)
        // doto returns x, not the result of the side effects
        assertTrue(result.hasArrayElements())
        assertEquals(3L, result.arraySize)
    }

    // as->

    @Test
    fun `as-then threads with explicit binding`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            as->: 10 it
              add(it, 32)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `as-then allows arbitrary position`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            as->: 1 it
              add(41, it)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `as-then with multiple steps`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            as->: 10 it
              add(it, 1)
              add(it, 31)
        """)
        assertEquals(42L, result.asLong())
    }

    // ?>

    @Test
    fun `nil-thread with non-nil seed`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("?>(10, add(32))").asLong())
    }

    @Test
    fun `nil-thread short-circuits on nil seed`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("?>(nil, add(32))").isNull)
    }

    @Test
    fun `nil-thread short-circuits on nil intermediate`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: nilIfBig(x) if(gt(x, 5), nil, x)
              ?>: 10
                nilIfBig()
                add(32)
        """)
        assertTrue(result.isNull)
    }

    @Test
    fun `nil-thread passes through multiple steps`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: nilIfBig(x) if(gt(x, 5), nil, x)
              ?>: 1
                add(1)
                nilIfBig()
                add(40)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `nil-thread with no steps returns seed`() = withContext { ctx ->
        assertEquals(42L, ctx.evalBridje("?>(42)").asLong())
    }

    @Test
    fun `nil-thread with no steps returns nil seed`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("?>(nil)").isNull)
    }

    // comment

    @Test
    fun `comment returns nil`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("comment(1, 2, 3)").isNull)
    }

    // Unquote-splicing tests

    @Test
    fun `unquote-splice in macro body`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: myDo(& body)
                '(do ~@body)
              myDo: println(1) add(10, 32)
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `unquote-splice mixed with unquote`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: myIf(cond, & branches)
                '(if ~cond ~@branches)
              myIf: true
                42
                0
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `unquote-splice with single element`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: wrap(& body)
                '(do ~@body)
              wrap: 42
        """)
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `unquote-splice at start of list`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: applyFn(& fnAndArgs)
                '(~@fnAndArgs)
              applyFn(add, 10, 32)
        """)
        assertEquals(42L, result.asLong())
    }

    // Type-checking of macro bodies — params are Form, rest param is [Form], body returns Form.

    @Test
    fun `macro param used as non-Form is a type error`() = withContext { ctx ->
        assertThrows<PolyglotException> {
            ctx.evalBridje("""
                defmacro: bad(x) add(x, 1)
            """.trimIndent())
        }
    }

    @Test
    fun `macro body returning non-Form is a type error`() = withContext { ctx ->
        assertThrows<PolyglotException> {
            ctx.evalBridje("""
                defmacro: bad(x) 42
            """.trimIndent())
        }
    }

    @Test
    fun `variadic rest param used as non-Vec-Form is a type error`() = withContext { ctx ->
        assertThrows<PolyglotException> {
            ctx.evalBridje("""
                defmacro: bad(& rest) add(rest, 1)
            """.trimIndent())
        }
    }
}
