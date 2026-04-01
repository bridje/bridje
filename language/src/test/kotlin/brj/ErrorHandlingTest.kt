package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ErrorHandlingTest {

    @Test
    fun `throw produces guest exception`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""throw("something went wrong")""")
        }
        assertTrue(ex.isGuestException)
        assertTrue(ex.message!!.contains("something went wrong"))
    }

    @Test
    fun `throw with tag value`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  deftag: Boom(msg)
                  throw(Boom("kaboom"))
            """.trimIndent())
        }
        assertTrue(ex.isGuestException)
    }

    @Test
    fun `try-catch catches thrown tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Boom
              try: throw(Boom)
                catch:
                  Boom 42
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `try-catch catches tag with bindings`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Problem(msg)
              try: throw(Problem("oops"))
                catch:
                  (Problem m) m
        """.trimIndent())
        assertEquals("oops", result.asString())
    }

    @Test
    fun `try-catch rethrows on no match`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  deftag: A
                  deftag: B
                  try: throw(B)
                    catch:
                      A 42
            """.trimIndent())
        }
        assertTrue(ex.isGuestException)
    }

    @Test
    fun `try-catch with default branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: A
              deftag: B
              try: throw(B)
                catch:
                  A 1
                  e 99
        """.trimIndent())
        assertEquals(99, result.asInt())
    }

    @Test
    fun `try-catch returns body value on no exception`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: A
              try: 42
                catch:
                  A 99
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `try-catch-finally runs finally on success`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: A
              try: 42
                catch:
                  A 99
                finally:
                  println("cleanup")
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `try-catch-finally runs finally on exception`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: A
              try: throw(A)
                catch:
                  A 42
                finally:
                  println("cleanup")
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `nested try-catch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Inner
              deftag: Outer
              try:
                try: throw(Inner)
                  catch:
                    Inner throw(Outer)
                catch:
                  Outer 42
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `catch-all binding pattern`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              deftag: Problem(code)
              try: throw(Problem(404))
                catch:
                  (Problem c) c
        """.trimIndent())
        assertEquals(404, result.asInt())
    }

    // Anomaly tags

    @Test
    fun `anomaly tags are available in brj core`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            case: NotFound({})
              (NotFound d) true
              x false
        """.trimIndent())
        assertTrue(result.asBoolean())
    }

    @Test
    fun `throw and catch anomaly tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: throw(NotFound({}))
              catch:
                (NotFound d) 42
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `all nine anomaly categories exist`() = withContext { ctx ->
        for (tag in listOf("Unavailable", "Interrupted", "Busy", "Incorrect", "Forbidden", "Unsupported", "NotFound", "Conflict", "Fault")) {
            val result = ctx.evalBridje("""
                case: $tag({})
                  ($tag d) true
                  x false
            """.trimIndent())
            assertTrue(result.asBoolean(), "$tag should be available")
        }
    }

    // Runtime exceptions are now guest exceptions

    @Test
    fun `division by zero is a guest exception`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("div(1, 0)")
        }
        assertTrue(ex.isGuestException)
    }

    @Test
    fun `case no-match is a guest exception`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  deftag: A
                  deftag: B
                  case: B
                    A 1
            """.trimIndent())
        }
        assertTrue(ex.isGuestException)
    }

    @Test
    fun `runtime errors are catchable`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: div(1, 0)
              catch:
                e 42
        """.trimIndent())
        assertEquals(42, result.asInt())
    }
}
