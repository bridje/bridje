package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TagTest {
    @Test
    fun `tag creates constructor in scope`() = withContext { ctx ->
        val constructor = ctx.evalBridje("tag: Just(value)")
        assertTrue(constructor.canExecute())
        assertEquals("Just", constructor.toString())
    }

    @Test
    fun `Just(42) creates tuple with value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Just(value)
              Just(42)
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(1, result.arraySize)
        assertEquals(42L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `tag Nothing creates singleton value`() = withContext { ctx ->
        val singleton = ctx.evalBridje("tag: Nothing")
        assertFalse(singleton.canExecute())
        assertFalse(singleton.canInstantiate())
        assertFalse(singleton.hasArrayElements())
        assertEquals("Nothing", singleton.toString())
    }

    @Test
    fun `Nothing is a singleton - same identity`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Nothing
              Nothing
        """.trimIndent())
        assertFalse(result.hasArrayElements())
        assertEquals("Nothing", result.toString())
    }

    @Test
    fun `nullary tag display string is just the tag name`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Nothing
              Nothing
        """.trimIndent())
        assertEquals("Nothing", result.toString())
    }

    @Test
    fun `unary tag display string includes value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Just(value)
              Just(42)
        """.trimIndent())
        assertEquals("Just(42)", result.toString())
    }

    @Test
    fun `multi-field tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Pair(first, second)
              Pair(1, 2)
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(2, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(2L, result.getArrayElement(1).asLong())
        assertEquals("Pair(1, 2)", result.toString())
    }

    @Test
    fun `arity mismatch - too few args`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  tag: Just(value)
                  Just()
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Arity") == true || ex.message?.contains("arity") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `arity mismatch - too many args`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  tag: Just(value)
                  Just(1, 2)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Arity") == true || ex.message?.contains("arity") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `tag name must be capitalized`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("tag: just(value)")
        }
        assertTrue(ex.message?.contains("capitalized") == true,
            "Expected capitalization error, got: ${ex.message}")
    }

    @Test
    fun `nested tuples`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Just(value)
              tag: Nothing
              Just(Just(42))
        """.trimIndent())
        assertEquals(1, result.arraySize)
        val inner = result.getArrayElement(0)
        assertEquals(1, inner.arraySize)
        assertEquals(42L, inner.getArrayElement(0).asLong())
    }

    // Interop tests

    @Test
    fun `constructor is executable and instantiable`() = withContext { ctx ->
        val constructor = ctx.evalBridje("tag: Just(value)")
        assertTrue(constructor.canExecute())
        assertTrue(constructor.canInstantiate())
    }

    @Test
    fun `can instantiate using constructor`() = withContext { ctx ->
        val constructor = ctx.evalBridje("tag: Just(value)")
        val result = constructor.newInstance(42L)
        assertTrue(result.hasArrayElements())
        assertEquals(1, result.arraySize)
        assertEquals(42L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `tagged tuple has meta object`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Just(value)
              Just(42)
        """.trimIndent())
        val meta = result.metaObject
        assertNotNull(meta)
        assertEquals("Just", meta.metaSimpleName)
        assertEquals("Just", meta.metaQualifiedName)
    }

    @Test
    fun `meta object isMetaInstance works`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: tag_meta_test
            tag: Just(value)
            tag: Other(value)
        """.trimIndent())

        val justConstructor = ctx.evalBridje("tag_meta_test/Just")
        val otherConstructor = ctx.evalBridje("tag_meta_test/Other")
        val justValue = ctx.evalBridje("tag_meta_test/Just(42)")
        val otherValue = ctx.evalBridje("tag_meta_test/Other(42)")

        assertTrue(justConstructor.isMetaInstance(justValue))
        assertFalse(justConstructor.isMetaInstance(otherValue))
        assertTrue(otherConstructor.isMetaInstance(otherValue))
        assertFalse(otherConstructor.isMetaInstance(justValue))
    }

    @Test
    fun `singleton is its own meta object`() = withContext { ctx ->
        val singleton = ctx.evalBridje("tag: Nothing")
        val meta = singleton.metaObject
        assertNotNull(meta)
        assertEquals("Nothing", meta.metaSimpleName)
        assertEquals("Nothing", meta.metaQualifiedName)
        assertTrue(meta.isMetaInstance(singleton))
    }

    @Test
    fun `constructor meta object for tagged tuple`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: tag_tuple_test
            tag: Just(value)
        """.trimIndent())

        val constructor = ctx.evalBridje("tag_tuple_test/Just")
        val tuple = ctx.evalBridje("tag_tuple_test/Just(42)")
        val meta = tuple.metaObject

        // The meta object should be the constructor
        assertTrue(meta.canExecute())
        assertTrue(meta.isMetaInstance(tuple))
    }

    @Test
    fun `parameterised tag with type variable`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: [t] Box(t)
              case: Box(42)
                Box(x) x
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `parameterised tag preserves type identity`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: [t] Wrapper(t)
              decl: [t] unwrap(Wrapper(t)) t
              def: unwrap(w) case: w
                Wrapper(x) x
              unwrap(Wrapper("hello"))
        """.trimIndent())
        assertEquals("hello", result.asString())
    }

}
