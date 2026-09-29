package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CaseTest {
    @Test
    fun `case matches nullary tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Nothing
              case: Nothing
                Nothing 42
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `case matches unary tag and binds value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              case: Just{.value 10}
                Just(r) .value(r)
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `case matches a tag alone, whatever its record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              case: Just{.value 10}
                Just 1
                2
        """.trimIndent())
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `case destructures a tag's record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .first Int
              decl: .second Int
              tag: Pair{.first, .second}
              case: Pair{.first 3, .second 4}
                Pair{first, second} [first, second]
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(3L, result.getArrayElement(0).asLong())
        assertEquals(4L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `case selects correct branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              enum: Maybe
                tag: Nothing
                tag: Just{.value}
              case: Just{.value 42}
                Nothing 0
                Just{value} value
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `case selects nothing branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              enum: Maybe
                tag: Nothing
                tag: Just{.value}
              case: Nothing
                Nothing 0
                Just{value} value
        """.trimIndent())
        assertEquals(0L, result.asLong())
    }

    @Test
    fun `case with default branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Nothing
              decl: .value Int
              tag: Just{.value}
              case: Nothing
                Just{value} value
                99
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `case default branch when tag does not match`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: A
              tag: B
              tag: C
              case: C
                A 1
                B 2
                3
        """.trimIndent())
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `case bindings are scoped to branch body`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              let: [value 100]
                case: Just{.value 42}
                  Just{value} value
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `case branch can return tagged value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              case: Just{.value 10}
                Just(r) Just(r)
        """.trimIndent())
        assertEquals("Just", result.metaObject.metaSimpleName)
        assertEquals(10L, result.getMember("value").asLong())
    }

    @Test
    fun `case that cannot match is a type error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  tag: A
                  tag: B
                  case: B
                    A 1
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("not a subtype") == true, "Expected type error, got: ${ex.message}")
    }

    @Test
    fun `case catchall binding pattern binds the scrutinee`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            case: 10
              x x
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `case binds an anomaly and reads its record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            case: Fault{.exnMessage "boom"}
              Fault(f) .?exnMessage(f)
              "none"
        """.trimIndent())
        assertEquals("boom", result.asString())
    }

    @Test
    fun `a tag pattern binds one record`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: .first Int
                  decl: .second Int
                  tag: Pair{.first, .second}
                  case: Pair{.first 3, .second 4}
                    Pair(a, b) a
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("a tag pattern binds one record") == true, "got: ${ex.message}")
    }

    @Test
    fun `a nullary tag has no record to bind`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  tag: Nothing
                  case: Nothing
                    Nothing(r) r
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("has no record to bind") == true, "got: ${ex.message}")
    }

    @Test
    fun `a destructured key must be known`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: .value Int
                  tag: Just{.value}
                  case: Just{.value 1}
                    Just{nope} nope
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Unknown key: .nope") == true, "got: ${ex.message}")
    }

    @Test
    fun `case rejects a nil pattern`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                case: nil
                  nil 42
                  0
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("use ifLet") == true, "Expected ifLet hint, got: ${ex.message}")
    }

    // A tag with no record carries no keys, so whether its type is open says nothing: the declared Done matches.
    @Test
    fun `a nullary tag declared as a type matches its pattern`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            tag: Done
            decl: finish() Done
            def: finish() Done
            def: g()
              case: finish()
                Done 1
            g()
        """.trimIndent())
        assertEquals(1L, result.asLong())
    }
}
