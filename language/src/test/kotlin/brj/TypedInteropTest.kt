package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TypedInteropTest {

    @Test
    fun `static field with typed interop`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.field
              import:
                java.time:
                  as(Instant, I)
            decl: I/EPOCH I
            def: epoch I/EPOCH
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.field/epoch")
        assertTrue(result.isInstant, "EPOCH should be an Instant")
    }

    @Test
    fun `static method with typed interop`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.static
              import:
                java.time:
                  as(Instant, I)
            decl: I/now() I
            def: instant I/now()
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.static/instant")
        assertTrue(result.isInstant, "now() should return an Instant")
    }

    @Test
    fun `static method with params`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.params
              import:
                java.time:
                  as(Instant, I)
            decl: I/ofEpochMilli(Int) I
            def: epoch I/ofEpochMilli(0)
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.params/epoch")
        assertTrue(result.isInstant, "ofEpochMilli should return an Instant")
    }

    @Test
    fun `instance method with typed interop`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.instance
              import:
                java.time:
                  as(Instant, I)
            decl: I/ofEpochMilli(Int) I
            decl: I/.toEpochMilli() Int
            def: roundtrip(ms) I/.toEpochMilli(I/ofEpochMilli(ms))
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.instance/roundtrip(1000)")
        assertEquals(1000L, result.asLong())
    }

    @Test
    fun `multiple interop decls`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.multi
              import:
                java.time:
                  as(Instant, I)
            decl: I/EPOCH I
            decl: I/now() I
            decl: I/.toEpochMilli() Int
            def: epochMillis I/.toEpochMilli(I/EPOCH)
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.multi/epochMillis")
        assertEquals(0L, result.asLong())
    }

    @Test
    fun `interop with threading macro`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.thread
              import:
                java.time:
                  as(Instant, I)
            decl: I/ofEpochMilli(Int) I
            decl: I/.toEpochMilli() Int
            def: roundtrip(ms)
              ->: I/ofEpochMilli(ms) I/.toEpochMilli()
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.thread/roundtrip(42)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `host method returning null arrives as nil`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.nullable
              import:
                java.lang:
                  as(System, Sys)
            decl: Sys/getProperty(Str) Str
            def: missing Sys/getProperty("no.such.property.exists")
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.nullable/missing")
        assertTrue(result.isNull, "null return from host method should be nil")
    }

    @Test
    fun `host method returning null works with case nil branch`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.nullable2
              import:
                java.lang:
                  as(System, Sys)
            decl: Sys/getProperty(Str) Str
            def: result
              case: Sys/getProperty("no.such.property.exists")
                nil "was nil"
                s s
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.nullable2/result")
        assertEquals("was nil", result.asString())
    }

    @Test
    fun `nullable return type declared in interop`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.nullable3
              import:
                java.lang:
                  as(System, Sys)
            decl: Sys/getProperty(Str) Str?
            def: missing Sys/getProperty("no.such.property.exists")
            def: present Sys/getProperty("java.version")
        """.trimIndent())
        val missing = ctx.evalBridje("test.interop.nullable3/missing")
        assertTrue(missing.isNull, "missing property should be nil")
        val present = ctx.evalBridje("test.interop.nullable3/present")
        assertFalse(present.isNull, "present property should not be nil")
        assertTrue(present.isString, "present property should be a string")
    }

    @Test
    fun `nullable interop return passed to non-null param is type error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                ns: test.interop.nullable4
                  import:
                    java.lang:
                      as(System, Sys)
                decl: Sys/getProperty(Str) Str?
                decl: Sys/getenv(Str) Str
                def: result
                  ->: Sys/getProperty("no.such.property") Sys/getenv()
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("nullable") == true, "Expected nullable type error, got: ${ex.message}")
    }

    @Test
    fun `non-nullable interop return passes type check`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.nullable5
              import:
                java.lang:
                  as(System, Sys)
            decl: Sys/getProperty(Str) Str
            decl: Sys/getenv(Str) Str
            def: result
              ->: Sys/getProperty("java.version") Sys/getenv()
        """.trimIndent())
        // If this evaluates without a type error, the non-nullable decl is accepted
    }

    @Test
    fun `interop instance method as first-class function`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.interop.firstclass
              import:
                java.time:
                  as(Instant, I)
            decl: I/ofEpochMilli(Int) I
            decl: I/.toEpochMilli() Int
            def: roundtrip(ms)
              let: [f I/.toEpochMilli]
                f(I/ofEpochMilli(ms))
        """.trimIndent())
        val result = ctx.evalBridje("test.interop.firstclass/roundtrip(99)")
        assertEquals(99L, result.asLong())
    }
}
