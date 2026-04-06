package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
