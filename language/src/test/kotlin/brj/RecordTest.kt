package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecordTest {

    @Test
    fun `defkey creates a key`() = withContext { ctx ->
        val key = ctx.evalBridje("defkey: foo Str")
        assertTrue(key.canExecute())
        assertEquals("foo", key.toString())
    }

    @Test
    fun `key is callable as getter`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: foo Str
              foo({foo 42})
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `record literal creates record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: foo Str
              {foo 42}
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals(42L, result.getMember("foo").asLong())
    }

    @Test
    fun `record with multiple fields`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: foo Str
              defkey: bar Int
              {foo "hello", bar 42}
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals("hello", result.getMember("foo").asString())
        assertEquals(42L, result.getMember("bar").asLong())
    }

    @Test
    fun `key getter extracts field from record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: name Str
              defkey: age Int
              let: [person {name "Alice", age 30}]
                person.name
        """.trimIndent())
        assertEquals("Alice", result.asString())
    }

    @Test
    fun `record display string`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: foo Str
              {foo 42}
        """.trimIndent())
        assertEquals("{foo 42}", result.toString())
    }

    @Test
    fun `record display string with multiple fields`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: a Str
              defkey: b Str
              {a 1, b 2}
        """.trimIndent())
        // Order might vary, just check it has the right format
        val str = result.toString()
        assertTrue(str.startsWith("{") && str.endsWith("}"))
        assertTrue(str.contains("a 1"))
        assertTrue(str.contains("b 2"))
    }

    @Test
    fun `key arity error - no args`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  defkey: foo Str
                  foo()
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Arity") == true || ex.message?.contains("arity") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `key arity error - too many args`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  defkey: foo Str
                  foo({foo 1}, {foo 2})
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Arity") == true || ex.message?.contains("arity") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `nested records`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: inner Str
              defkey: outer Str
              {outer {inner 42}}
        """.trimIndent())
        assertTrue(result.hasMembers())
        val inner = result.getMember("outer")
        assertTrue(inner.hasMembers())
        assertEquals(42L, inner.getMember("inner").asLong())
    }

    @Test
    fun `key on nested record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: inner Str
              defkey: outer Str
              {outer {inner 42}}.outer.inner
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `record in vector`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: x Int
              [{x 1}, {x 2}, {x 3}]
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(3, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).getMember("x").asLong())
        assertEquals(2L, result.getArrayElement(1).getMember("x").asLong())
        assertEquals(3L, result.getArrayElement(2).getMember("x").asLong())
    }

    @Test
    fun `record field can be function result`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: sum Int
              {sum add(1 2)}
        """.trimIndent())
        assertEquals(3L, result.getMember("sum").asLong())
    }
}
