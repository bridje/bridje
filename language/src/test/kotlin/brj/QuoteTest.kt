package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QuoteTest {
    @Test
    fun `quote symbol returns SymbolForm`() = withContext { ctx ->
        val result = ctx.evalBridje("'foo")
        assertEquals("SymbolForm", result.metaObject.metaSimpleName)
        assertEquals("foo", result.toString())
    }

    @Test
    fun `quote list returns ListForm`() = withContext { ctx ->
        val result = ctx.evalBridje("'(1 2 3)")
        assertEquals("List", result.metaObject.metaSimpleName)
        assertEquals("(1 2 3)", result.toString())
    }

    @Test
    fun `quote vector returns VectorForm`() = withContext { ctx ->
        val result = ctx.evalBridje("'[a b c]")
        assertEquals("Vector", result.metaObject.metaSimpleName)
        assertEquals("[a b c]", result.toString())
    }

    @Test
    fun `quote int returns IntForm`() = withContext { ctx ->
        val result = ctx.evalBridje("'42")
        assertEquals("Int", result.metaObject.metaSimpleName)
        assertEquals("42", result.toString())
    }

    @Test
    fun `quote string returns StringForm`() = withContext { ctx ->
        val result = ctx.evalBridje("'\"hello\"")
        assertEquals("String", result.metaObject.metaSimpleName)
        assertEquals("\"hello\"", result.toString())
    }

    @Test
    fun `nested quote`() = withContext { ctx ->
        val result = ctx.evalBridje("''foo")
        assertEquals("List", result.metaObject.metaSimpleName)
        assertEquals("(quote foo)", result.toString())
    }

    @Test
    fun `case on quoted symbol`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("""
            case: 'foo
              rdr/SymbolForm 1
              rdr/List 2
              3
        """.trimIndent())
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `case on quoted list`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("""
            case: '(a b c)
              rdr/SymbolForm 1
              rdr/List 2
              3
        """.trimIndent())
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `case on quoted vector`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("""
            case: '[1 2]
              rdr/List 1
              rdr/Vector 2
              3
        """.trimIndent())
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `case with default on quoted form`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("""
            case: '42
              rdr/SymbolForm 1
              rdr/List 2
              99
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `quoted list has array element containing forms`() = withContext { ctx ->
        val result = ctx.evalBridje("'(a b c)")
        assertTrue(result.hasArrayElements())
        assertEquals(1L, result.arraySize)
        val forms = result.getArrayElement(0)
        assertTrue(forms.hasArrayElements())
        assertEquals(3L, forms.arraySize)
        assertEquals("SymbolForm", forms.getArrayElement(0).metaObject.metaSimpleName)
        assertEquals("a", forms.getArrayElement(0).toString())
    }

    @Test
    fun `quoted vector has array element containing forms`() = withContext { ctx ->
        val result = ctx.evalBridje("'[1 2]")
        assertTrue(result.hasArrayElements())
        assertEquals(1L, result.arraySize)
        val forms = result.getArrayElement(0)
        assertTrue(forms.hasArrayElements())
        assertEquals(2L, forms.arraySize)
        assertEquals("Int", forms.getArrayElement(0).metaObject.metaSimpleName)
    }

    @Test
    fun `case can bind forms from quoted list`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("""
            case: '(foo bar)
              rdr/List(forms) forms
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(2L, result.arraySize)
        assertEquals("foo", result.getArrayElement(0).toString())
        assertEquals("bar", result.getArrayElement(1).toString())
    }

    @Test
    fun `syntax-quote resolves brj core builtin`() = withContext { ctx ->
        val result = ctx.evalBridje("`count")
        assertEquals("QSymbolForm",result.metaObject.metaSimpleName)
        assertEquals("brj.core/count", result.toString())
    }

    @Test
    fun `syntax-quote errors on unknown symbol`() = withContext { ctx ->
        assertThrows<RuntimeException> {
            ctx.evalBridje("`noSuchThingExists")
        }
    }

    @Test
    fun `syntax-quote inside unquote in macro`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: useCount(xs)
                '(~`count ~xs)
              useCount([1 2 3])
        """)
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `syntax-quote on fully qualified symbol`() = withContext { ctx ->
        val result = ctx.evalBridje("`brj.core/count")
        assertEquals("QSymbolForm",result.metaObject.metaSimpleName)
        assertEquals("brj.core/count", result.toString())
    }

    @Test
    fun `syntax-quote errors on unknown qualified symbol`() = withContext { ctx ->
        assertThrows<RuntimeException> {
            ctx.evalBridje("`brj.core/noSuchThing")
        }
    }

    @Test
    fun `syntax-quote on require alias resolves to real namespace`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: foo.lib
            def: bar 42
        """.trimIndent())

        val result = ctx.evalBridje("""
            ns: consumer
              require:
                foo:
                  as(lib, f)
            def: result `f/bar
        """.trimIndent())

        val resolved = result.getMember("result")
        assertEquals("QSymbolForm",resolved.metaObject.metaSimpleName)
        assertEquals("foo.lib/bar", resolved.toString())
    }

    @Test
    fun `syntax-quote recursive walk resolves globals in a list`() = withContext { ctx ->
        val result = ctx.evalBridje("`(count [1 2 3])")
        assertEquals("(brj.core/count [1 2 3])", result.toString())
    }

    @Test
    fun `syntax-quote leaves special forms bare`() = withContext { ctx ->
        val result = ctx.evalBridje("`(if true 1 2)")
        assertEquals("(if true 1 2)", result.toString())
    }

    @Test
    fun `syntax-quote supports unquote inside recursive walk`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defmacro: inc-it(x) `(add ~x 1)
              inc-it(5)
        """.trimIndent())
        assertEquals(6L, result.asLong())
    }

    @Test
    fun `syntax-quote errors at macro-def time on unresolvable symbol`() = withContext { ctx ->
        val ex = assertThrows<RuntimeException> {
            ctx.evalBridje("""
                do:
                  defmacro: broken(x) `(no-such-fn ~x)
                  1
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("no-such-fn") == true,
            "Expected error to name 'no-such-fn', got: ${ex.message}")
    }

    @Test
    fun `syntax-quote preserves gensym suffix`() = withContext { ctx ->
        val result = ctx.evalBridje("`(let [x# 1] x#)")
        val str = result.toString()
        // Both x#'s should expand to the same gensym'd name
        val inner = str.substringAfter("[").substringBefore(" ")
        val body = str.substringAfterLast(" ").removeSuffix(")")
        assertEquals(inner, body, "gensym'd names should match")
        assertTrue(inner.startsWith("x__"), "expected gensym prefix, got $inner")
    }
}
