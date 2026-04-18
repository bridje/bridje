package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ErrorHandlingTest {

    @Test
    fun `throw produces guest exception`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""throw(Fault({:exnMessage "something went wrong"}))""")
        }
        assertTrue(ex.isGuestException)
        assertTrue(ex.message!!.contains("something went wrong"))
    }

    @Test
    fun `throw anomaly with data`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""throw(Fault({:exnMessage "kaboom"}))""")
        }
        assertTrue(ex.isGuestException)
    }

    @Test
    fun `try-catch catches anomaly`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: throw(Fault({}))
              catch:
                (Fault d) 42
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `try-catch catches anomaly with bindings`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: throw(Fault({:exnMessage "oops"}))
              catch:
                (Fault d) :exnMessage(d)
        """.trimIndent())
        assertEquals("oops", result.asString())
    }

    @Test
    fun `try-catch rethrows on no match`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                try: throw(Conflict({}))
                  catch:
                    (NotFound d) 42
            """.trimIndent())
        }
        assertTrue(ex.isGuestException)
    }

    @Test
    fun `try-catch with default branch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: throw(Conflict({}))
              catch:
                (NotFound d) 1
                e 99
        """.trimIndent())
        assertEquals(99, result.asInt())
    }

    @Test
    fun `try-catch returns body value on no exception`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: 42
              catch:
                (Fault d) 99
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `try-catch-finally runs finally on success`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: 42
              catch:
                (Fault d) 99
              finally:
                println("cleanup")
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `try-catch-finally runs finally on exception`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: throw(Fault({}))
              catch:
                (Fault d) 42
              finally:
                println("cleanup")
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `nested try-catch`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try:
              try: throw(NotFound({}))
                catch:
                  (NotFound d) throw(Conflict({}))
              catch:
                (Conflict d) 42
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `catch-all binding pattern`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: throw(Incorrect({:exnMessage "bad input"}))
              catch:
                e 42
        """.trimIndent())
        assertEquals(42, result.asInt())
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
    fun `all ten anomaly categories exist`() = withContext { ctx ->
        for (tag in listOf("Unavailable", "Interrupted", "Busy", "Incorrect", "Forbidden", "Unsupported", "NotFound", "Conflict", "Fault", "Host")) {
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
                  tag: A
                  tag: B
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

    @Test
    fun `runtime errors are catchable by category`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            try: div(1, 0)
              catch:
                (Incorrect d) 42
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `host InterruptedException caught as Interrupted anomaly`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.interrupt
              import:
                java.lang:
                  as(Thread, T)
            decl: T/sleep(Int) Nothing
            decl: T/currentThread() T
            decl: T/.interrupt() Nothing
            def: result
              do:
                T/.interrupt(T/currentThread())
                try: T/sleep(100)
                  catch:
                    Interrupted(_) "caught"
        """.trimIndent())
        assertEquals("caught", ctx.evalBridje("test.interrupt/result").asString())
    }

    @Test
    fun `host RuntimeException caught as Host anomaly`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.hostex
              import:
                java.lang:
                  as(Integer, Int)
            decl: Int/parseInt(Str) Int
            def: result
              try: Int/parseInt("not a number")
                catch:
                  Host(d) :exnMessage(d)
        """.trimIndent())
        val msg = ctx.evalBridje("test.hostex/result").asString()
        assertTrue(msg.contains("not a number"), "Expected message about bad input, got: $msg")
    }

    @Test
    fun `exnMessage key provides exception message`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""throw(NotFound({:exnMessage "user 123 not found"}))""")
        }
        assertTrue(ex.isGuestException)
        assertEquals("user 123 not found", ex.message)
    }
}
