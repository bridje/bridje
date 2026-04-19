package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LangTest {

    @Test
    fun `lang value form evaluates JS expression`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            lang: "js" Int
              "40 + 2"
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `lang fn form produces callable`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: add
                lang: "js" Fn([Int Int] Int)
                  "(a, b) => a + b"
              add(2, 3)
        """.trimIndent())
        assertEquals(5L, result.asLong())
    }

    @Test
    fun `lang fn returning a string`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              def: greet
                lang: "js" Fn([Str] Str)
                  "(who) => 'hello, ' + who"
              greet("world")
        """.trimIndent())
        assertTrue(result.isString, "Expected string, got $result")
        assertEquals("hello, world", result.asString())
    }

    @Test
    fun `lang float value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            lang: "js" Double
              "Math.PI"
        """.trimIndent())
        assertEquals(Math.PI, result.asDouble(), 1e-10)
    }

    @Test
    fun `lang without code string errors at analyse time`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                lang: "js" Int
            """.trimIndent())
        }
        assertTrue(
            ex.message?.contains("lang requires a code string") == true,
            "Expected 'lang requires a code string', got: ${ex.message}"
        )
    }

    @Test
    fun `lang without language literal errors at analyse time`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                lang: js Int "42"
            """.trimIndent())
        }
        assertTrue(
            ex.message?.contains("lang requires a language name") == true,
            "Expected 'lang requires a language name', got: ${ex.message}"
        )
    }

    @Test
    fun `lang with unavailable language errors at analyse time`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                lang: "fortran77" Int
                  "42"
            """.trimIndent())
        }
        assertTrue(
            ex.message?.contains("Language 'fortran77' is not available") == true,
            "Expected 'Language not available' error, got: ${ex.message}"
        )
    }
}
