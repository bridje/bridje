package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CallConventionTest {

    private fun wrongArity(ctx: Context, src: String, expected: Int, got: Int) {
        val ex = assertThrows(PolyglotException::class.java) { ctx.evalBridje(src) }
        val atRuntime = "Wrong arity: expected $expected, got $got"
        val atChecker = "function arity mismatch: $expected vs $got"
        assertTrue(ex.message?.let { it.contains(atRuntime) || it.contains(atChecker) } == true, "got: ${ex.message}")
    }

    @Test
    fun `a missing trailing argument is the empty record`() = withContext { ctx ->
        assertEquals("{}", ctx.evalBridje("meta()").toString())
        assertEquals("{}", ctx.evalBridje("println()").toString())
        assertEquals("{}", ctx.evalBridje("do:\n  def: second(x, y) y\n  second(1)").toString())
    }

    @Test
    fun `a surplus argument is an arity error`() = withContext { ctx ->
        wrongArity(ctx, "not(true, {})", 1, 2)
        wrongArity(ctx, "do:\n  def: second(x, y) y\n  second(1, 2, 3)", 2, 3)
        wrongArity(ctx, "do:\n  decl: .k Int\n  .k({.k 1}, {})", 1, 2)
        wrongArity(ctx, "do:\n  decl: .k Int\n  tag: Boxed{.k}\n  Boxed({.k 2}, {})", 1, 2)
    }

    @Test
    fun `a host member is called at its declared arity`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.call.host
              import:
                java.lang:
                  as(Math, M)
            decl: M/abs(Int) Int
            def: abs M/abs(-1)
        """.trimIndent())
        assertEquals(1L, ctx.evalBridje("test.call.host/abs").asLong())
        wrongArity(ctx, """
            ns: test.call.host.surplus
              import:
                java.lang:
                  as(Math, M)
            decl: M/abs(Int) Int
            def: abs M/abs(-1, {})
        """.trimIndent(), 1, 2)
    }
}
