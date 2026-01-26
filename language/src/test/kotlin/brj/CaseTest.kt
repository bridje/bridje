package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CaseTest {
    @Test
    fun `case matches nullary tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Nothing
              case: Nothing
                Nothing 42
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `case matches unary tag and binds value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              case: Just(10)
                Just(x) x
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `case matches multi-field tag and binds values`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Pair(first, second)
              case: Pair(3, 4)
                Pair(a, b) [a, b]
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(3L, result.getArrayElement(0).asLong())
        assertEquals(4L, result.getArrayElement(1).asLong())
    }

    @Test
    fun `case selects correct branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Nothing
              deftag: Just(value)
              case: Just(42)
                Nothing 0
                Just(x) x
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `case selects nothing branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Nothing
              deftag: Just(value)
              case: Nothing
                Nothing 0
                Just(x) x
        """.trimIndent())
        assertEquals(0L, result.asLong())
    }

    @Test
    fun `case with default branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Nothing
              deftag: Just(value)
              case: Nothing
                Just(x) x
                99
        """.trimIndent())
        assertEquals(99L, result.asLong())
    }

    @Test
    fun `case default branch when tag does not match`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: A
              deftag: B
              deftag: C
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
              deftag: Just(value)
              let: [x 100]
                case: Just(42)
                  Just(x) x
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `case branch can return tagged value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Just(value)
              case: Just(10)
                Just(x) Just(x)
        """.trimIndent())
        assertEquals("Just", result.metaObject.metaSimpleName)
        assertEquals(10L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `case throws when no branch matches`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  deftag: A
                  deftag: B
                  case: B
                    A 1
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("No matching") == true)
    }

    @Test
    fun `case matches nil pattern`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            case: nil
              nil 42
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `case catchall binding pattern matches non-nil`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            case: 10
              nil 0
              x x
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `case nil vs catchall binding pattern`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            case: nil
              nil "was nil"
              x "was not nil"
        """.trimIndent())
        assertEquals("was nil", result.asString())
    }
}
