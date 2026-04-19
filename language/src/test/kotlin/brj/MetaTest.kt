package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetaTest {

    @Test
    fun `vector has empty meta by default`() = withContext { ctx ->
        val result = ctx.evalBridje("meta([1 2 3])")
        assertTrue(result.hasMembers())
        assertEquals(0, result.memberKeys.size)
    }

    @Test
    fun `with-meta adds meta to vector`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: :foo Str
              meta(with-meta([1 2 3], {:foo "bar"}))
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals("bar", result.getMember("foo").asString())
    }

    @Test
    fun `with-meta preserves vector contents`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: :foo Str
              with-meta([1 2 3], {:foo "bar"})
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(3, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(2L, result.getArrayElement(1).asLong())
        assertEquals(3L, result.getArrayElement(2).asLong())
    }

    @Test
    fun `record has empty meta by default`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: :x Int
              meta({:x 42})
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals(0, result.memberKeys.size)
    }

    @Test
    fun `with-meta adds meta to record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: {:x Int, :tag Str}
              meta(with-meta({:x 42}, {:tag "special"}))
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals("special", result.getMember("tag").asString())
    }

    @Test
    fun `with-meta preserves record contents`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: {:x Int, :tag Str}
              with-meta({:x 42}, {:tag "special"})
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals(42L, result.getMember("x").asLong())
    }

    @Test
    fun `meta does not leak into record members`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: {:x Int, :tag Str}
              with-meta({:x 42}, {:tag "special"})
        """.trimIndent())
        assertFalse(result.hasMember("tag"))
    }

    @Test
    fun `with-meta nil is a type error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: :foo Str
                  let: [v with-meta([1 2], {:foo "bar"})]
                    meta(with-meta(v, nil))
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("nullable") == true, "Expected nullable type error, got: ${ex.message}")
    }
}
