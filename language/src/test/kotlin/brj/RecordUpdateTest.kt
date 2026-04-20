package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecordUpdateTest {

    @Test
    fun `with updates a single field`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: :foo Str
              let: [r {:foo 1}]
                with(r, .foo 99)
        """.trimIndent())
        assertEquals(99L, result.getMember("foo").asLong())
    }

    @Test
    fun `with returns a new record without mutating the original`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: :foo Str
              let: [r {:foo 1}
                    updated with(r, .foo 99)]
                [(:foo r), (:foo updated)]
        """.trimIndent())
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(99L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `with updates multiple fields`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: {:a Str, :b Str, :c Str}
              let: [r {:a 1, :b 2, :c 3}]
                with(r, .a 10, .b 20)
        """.trimIndent())
        assertEquals(10L, result.getMember("a").asLong())
        assertEquals(20L, result.getMember("b").asLong())
        assertEquals(3L, result.getMember("c").asLong())
    }

    @Test
    fun `with preserves unupdated fields`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: {:a Str, :b Str}
              with({:a 1, :b 2}, .a 99)
        """.trimIndent())
        assertEquals(99L, result.getMember("a").asLong())
        assertEquals(2L, result.getMember("b").asLong())
    }

    @Test
    fun `with via list syntax`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: :foo Str
              (with {:foo 1} .foo 99)
        """.trimIndent())
        assertEquals(99L, result.getMember("foo").asLong())
    }

    @Test
    fun `with rejects unknown field`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: :foo Str
                  with({:foo 1}, .bar 99)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Unknown field") == true,
            "Expected unknown-field error, got: ${ex.message}")
        assertTrue(ex.message?.contains("bar") == true,
            "Expected error to mention the unknown field name, got: ${ex.message}")
    }

    @Test
    fun `with rejects odd number of field forms`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: :foo Str
                  with({:foo 1}, .foo)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("even number of field") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `with rejects non-dot-symbol field selector`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: :foo Str
                  with({:foo 1}, :foo 99)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("dot-symbol") == true,
            "Expected dot-symbol error, got: ${ex.message}")
    }

    @Test
    fun `with requires a record and at least one field update`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: :foo Str
                  with({:foo 1})
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("at least one field") == true,
            "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `with on qualified key`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: my.keys
            decl: :foo Str
        """.trimIndent())

        val ns = ctx.evalBridje("""
            ns: consumer
              require:
                my:
                  as(keys, k)
            def: result
              with({:k/foo 1}, k/.foo 99)
        """.trimIndent())

        assertEquals(99L, ns.getMember("result").getMember("foo").asLong())
    }

    @Test
    fun `with composes cleanly`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: {:x Int, :y Int, :z Int}
              let: [r {:x 1, :y 2, :z 3}
                    r2 with(r, .x 10)
                    r3 with(r2, .y 20, .z 30)]
                r3
        """.trimIndent())
        assertEquals(10L, result.getMember("x").asLong())
        assertEquals(20L, result.getMember("y").asLong())
        assertEquals(30L, result.getMember("z").asLong())
    }

    @Test
    fun `with value expressions are evaluated`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: {:a Int, :b Int}
              let: [r {:a 1, :b 2}]
                with(r, .a add(10, 20), .b mul(3, 4))
        """.trimIndent())
        assertEquals(30L, result.getMember("a").asLong())
        assertEquals(12L, result.getMember("b").asLong())
    }
}
