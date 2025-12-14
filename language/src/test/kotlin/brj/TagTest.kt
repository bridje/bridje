package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TagTest {
    @Test
    fun `deftag creates constructor in scope`() = withContext { ctx ->
        val constructor = ctx.evalBridje("deftag: Just(value)")
        assertTrue(constructor.canExecute())
        assertEquals("Just", constructor.toString())
    }

    @Test
    fun `Just(42) creates tuple with value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              Just(42)
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(1, result.arraySize)
        assertEquals(42L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `deftag Nothing creates singleton value`() = withContext { ctx ->
        val singleton = ctx.evalBridje("deftag: Nothing")
        assertFalse(singleton.canExecute())
        assertFalse(singleton.canInstantiate())
        assertFalse(singleton.hasArrayElements())
        assertEquals("Nothing", singleton.toString())
    }

    @Test
    fun `Nothing is a singleton - same identity`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Nothing
              Nothing
        """.trimIndent())
        assertFalse(result.hasArrayElements())
        assertEquals("Nothing", result.toString())
    }

    @Test
    fun `nullary tag display string is just the tag name`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Nothing
              Nothing
        """.trimIndent())
        assertEquals("Nothing", result.toString())
    }

    @Test
    fun `unary tag display string includes value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              Just(42)
        """.trimIndent())
        assertEquals("Just(42)", result.toString())
    }

    @Test
    fun `multi-field tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Pair(first, second)
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
                  deftag: Just(value)
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
                  deftag: Just(value)
                  Just(1, 2)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Arity") == true || ex.message?.contains("arity") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `tag name must be capitalized`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("deftag: just(value)")
        }
        assertTrue(ex.message?.contains("capitalized") == true,
            "Expected capitalization error, got: ${ex.message}")
    }

    @Test
    fun `nested tuples`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              deftag: Nothing
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
        val constructor = ctx.evalBridje("deftag: Just(value)")
        assertTrue(constructor.canExecute())
        assertTrue(constructor.canInstantiate())
    }

    @Test
    fun `can instantiate using constructor`() = withContext { ctx ->
        val constructor = ctx.evalBridje("deftag: Just(value)")
        val result = constructor.newInstance(42L)
        assertTrue(result.hasArrayElements())
        assertEquals(1, result.arraySize)
        assertEquals(42L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `tagged tuple has meta object`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              Just(42)
        """.trimIndent())
        val meta = result.metaObject
        assertNotNull(meta)
        assertEquals("Just", meta.metaSimpleName)
        assertEquals("Just", meta.metaQualifiedName)
    }

    @Test
    fun `meta object isMetaInstance works`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              deftag: Other(value)
              [Just, Other, Just(42), Other(42)]
        """.trimIndent())
        val justConstructor = result.getArrayElement(0)
        val otherConstructor = result.getArrayElement(1)
        val justValue = result.getArrayElement(2)
        val otherValue = result.getArrayElement(3)

        assertTrue(justConstructor.isMetaInstance(justValue))
        assertFalse(justConstructor.isMetaInstance(otherValue))
        assertTrue(otherConstructor.isMetaInstance(otherValue))
        assertFalse(otherConstructor.isMetaInstance(justValue))
    }

    @Test
    fun `singleton is its own meta object`() = withContext { ctx ->
        val singleton = ctx.evalBridje("deftag: Nothing")
        val meta = singleton.metaObject
        assertNotNull(meta)
        assertEquals("Nothing", meta.metaSimpleName)
        assertEquals("Nothing", meta.metaQualifiedName)
        assertTrue(meta.isMetaInstance(singleton))
    }

    @Test
    fun `constructor meta object for tagged tuple`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              [Just, Just(42)]
        """.trimIndent())
        val constructor = result.getArrayElement(0)
        val tuple = result.getArrayElement(1)
        val meta = tuple.metaObject

        // The meta object should be the constructor
        assertTrue(meta.canExecute())
        assertTrue(meta.isMetaInstance(tuple))
    }
}
