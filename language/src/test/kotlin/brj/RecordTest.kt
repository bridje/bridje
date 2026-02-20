package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecordTest {

    @Test
    fun `defkey creates a key`() = withContext { ctx ->
        val key = ctx.evalBridje("defkey: :foo Str")
        assertTrue(key.canExecute())
        assertEquals("foo", key.toString())
    }

    @Test
    fun `key is callable as getter`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :foo Str
              :foo({:foo 42})
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `record literal creates record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :foo Str
              {:foo 42}
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals(42L, result.getMember("foo").asLong())
    }

    @Test
    fun `record with multiple fields`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :foo Str
              defkey: :bar Int
              {:foo "hello", :bar 42}
        """.trimIndent())
        assertTrue(result.hasMembers())
        assertEquals("hello", result.getMember("foo").asString())
        assertEquals(42L, result.getMember("bar").asLong())
    }

    @Test
    fun `key getter extracts field from record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :name Str
              defkey: :age Int
              let: [person {:name "Alice", :age 30}]
                person.name
        """.trimIndent())
        assertEquals("Alice", result.asString())
    }

    @Test
    fun `record display string`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :foo Str
              {:foo 42}
        """.trimIndent())
        assertEquals("{foo 42}", result.toString())
    }

    @Test
    fun `record display string with multiple fields`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :a Str
              defkey: :b Str
              {:a 1, :b 2}
        """.trimIndent())
        val str = result.toString()
        assertTrue(str.startsWith("{") && str.endsWith("}"))
        assertTrue(str.contains("a 1"))
        assertTrue(str.contains("b 2"))
    }

    @Test
    fun `optional key returns nil when missing`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :name Str
              ?name({})
        """.trimIndent())
        assertTrue(result.isNull)
    }

    @Test
    fun `optional key returns value when present`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :name Str
              ?name({:name "Alice"})
        """.trimIndent())
        assertEquals("Alice", result.asString())
    }

    @Test
    fun `optional key display string`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :name Str
              ?name
        """.trimIndent())
        assertEquals("?name", result.toString())
    }

    @Test
    fun `key arity error - no args`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  defkey: :foo Str
                  :foo()
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
                  defkey: :foo Str
                  :foo({:foo 1}, {:foo 2})
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Arity") == true || ex.message?.contains("arity") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `nested records`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :inner Str
              defkey: :outer Str
              {:outer {:inner 42}}
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
              defkey: :inner Str
              defkey: :outer Str
              {:outer {:inner 42}}.outer.inner
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `record in vector`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :x Int
              [{:x 1}, {:x 2}, {:x 3}]
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
              defkey: :sum Int
              {:sum add(1 2)}
        """.trimIndent())
        assertEquals(3L, result.getMember("sum").asLong())
    }

    @Test
    fun `keyword in value position resolves to key`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :foo Str
              :foo
        """.trimIndent())
        assertTrue(result.canExecute())
        assertEquals("foo", result.toString())
    }

    @Test
    fun `keyword metadata shorthand`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :test Str
              ^:test def: myVal 42
              myVal
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `optional keyword syntax`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :name Str
              (:?name {:name "Alice"})
        """.trimIndent())
        assertEquals("Alice", result.asString())
    }

    @Test
    fun `optional keyword returns nil when missing`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defkey: :name Str
              (:?name {})
        """.trimIndent())
        assertTrue(result.isNull)
    }

    @Test
    fun `qualified keyword across namespaces`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: my:keys
            defkey: :foo Str
        """.trimIndent())

        val ns = ctx.evalBridje("""
            ns: consumer
              require:
                my:
                  keys.as(k)
            def: result (:k:foo {:k:foo 42})
        """.trimIndent())

        assertEquals(42L, ns.getMember("result").asLong())
    }

    @Test
    fun `qualified keyword in record literal`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: my:keys
            defkey: :foo Str
        """.trimIndent())

        val ns = ctx.evalBridje("""
            ns: consumer
              require:
                my:
                  keys.as(k)
            def: result {:k:foo 42}
        """.trimIndent())

        assertEquals(42L, ns.getMember("result").getMember("foo").asLong())
    }

    @Test
    fun `qualified field access across namespaces`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: my:keys
            defkey: :bar Str
        """.trimIndent())

        val ns = ctx.evalBridje("""
            ns: consumer
              require:
                my:
                  keys.as(u)
            def: result {:u:bar 99}.u:bar
        """.trimIndent())

        assertEquals(99L, ns.getMember("result").asLong())
    }
}
