package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnumTest {

    @Test
    fun `basic enum declaration`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Direction
              tag: North
              tag: South
              tag: East
              tag: West
            def: result North
        """.trimIndent())
        assertNotNull(result)
    }

    @Test
    fun `enum with payloads`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Result(a, e)
              tag: Ok(a)
              tag: Err(e)
            Ok(42)
        """.trimIndent())
        assertTrue(result.hasArrayElements())
        assertEquals(42L, result.getArrayElement(0).asLong())
    }

    @Test
    fun `case matching on enum`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Maybe(a)
              tag: Just(a)
              tag: Nothing
            def: fromMaybe(m, default)
              case: m
                Just(v) v
                Nothing default
            fromMaybe(Just(42), 0)
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `enum variants are accessible as constructors`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Color
              tag: Red
              tag: Green
              tag: Blue
            case: Green
              Red 1
              Green 2
              Blue 3
        """.trimIndent())
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `exhaustive case matching passes`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Color
              tag: Red
              tag: Green
              tag: Blue
            def: name(c)
              case: c
                Red "red"
                Green "green"
                Blue "blue"
            def: result name(Red)
        """.trimIndent())
        assertEquals("red", result.asString())
    }

    @Test
    fun `non-exhaustive case matching is an error`() = withContext { ctx ->
        val ex = assertThrows<PolyglotException> {
            ctx.evalBridje("""
                enum: Color
                  tag: Red
                  tag: Green
                  tag: Blue
                def: name(c)
                  case: c
                    Red "red"
                    Green "green"
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Non-exhaustive") == true || ex.message?.contains("missing") == true,
            "Expected exhaustiveness error, got: ${ex.message}")
    }

    @Test
    fun `case with default branch skips exhaustiveness check`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Color
              tag: Red
              tag: Green
              tag: Blue
            def: isRed(c)
              case: c
                Red true
                other false
            def: result isRed(Red)
        """.trimIndent())
        assertTrue(result.asBoolean())
    }

    @Test
    fun `enum variant types as enum type`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Maybe(a)
              tag: Just(a)
              tag: Nothing
            decl: unwrap(Maybe(Int)) Int
            def: unwrap(m)
              case: m
                Just(v) v
                Nothing 0
            unwrap(Just(42))
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `enum type in function return`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            enum: Result(a, e)
              tag: Ok(a)
              tag: Err(e)
            def: tryDiv(a, b)
              if: eq(b, 0)
                Err("division by zero")
                Ok(div(a, b))
            case: tryDiv(10, 2)
              Ok(v) v
              Err(e) 0
        """.trimIndent())
        assertEquals(5L, result.asLong())
    }
}
