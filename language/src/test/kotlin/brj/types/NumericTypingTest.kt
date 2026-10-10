package brj.types

import brj.evalBridje
import brj.runtime.BridjeContext
import brj.runtime.sym
import brj.withContext
import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NumericTypingTest {

    private fun rejected(src: String, message: String) = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) { ctx.evalBridje(src) }
        assertTrue(ex.message?.contains(message) == true, "got: ${ex.message}")
    }

    private fun shown(src: String, name: String) = withContext { ctx ->
        ctx.evalBridje("ns: numeric.typing\n$src")
        val bctx = BridjeContext.current()
        bctx.namespaces["numeric.typing".sym]!!.vars[name.sym]!!.scheme!!.simplify(typeCtx(bctx)).toString()
    }

    @Test
    fun `arithmetic and comparison reject what is not numeric`() {
        rejected("add(\"a\", \"b\")", "Str is not numeric")
        rejected("lt(true, false)", "Bool is not numeric")
        rejected("mul(not, 1)", "Fn([Bool] Bool) is not numeric")
    }

    @Test
    fun `arithmetic and comparison reject nil`() {
        rejected("add(nth([1, nil], 0), 1)", "nil is not numeric")
        rejected("gt(nil, 1)", "nil is not numeric")
    }

    @Test
    fun `a definition over a numeric variable is rejected at a use that is not`() {
        rejected("do:\n  def: double(x) add(x, x)\n  double(\"a\")", "Str is not numeric")
        rejected("do:\n  def: double(x) add(x, x)\n  double(nil)", "nil is not numeric")
    }

    @Test
    fun `a declaration cannot be more general than the numeric operation it uses`() {
        rejected("decl: [a] double(a) a\ndef: double(x) add(x, x)", "more general than the definition")
    }

    @Test
    fun `a numeric variable's join with nil is still nil-able`() {
        assertEquals("Fn([Int] Int?)", shown("def: f(x) if: lt(x, 0) nil x", "f"))
    }

    @Test
    fun `the numeric builtins render their constraint`() {
        assertEquals("[a, Num(a)] Fn([a, a] a)", shown("def: f add", "f"))
        assertEquals("[a, Num(a)] Fn([a, a] Bool)", shown("def: f lt", "f"))
        assertEquals("[a, Num(a)] Fn([a] a)", shown("def: f(x) add(x, x)", "f"))
    }

    @Test
    fun `a numeric variable met by a primitive is that primitive`() {
        assertEquals("Fn([Int] Int)", shown("def: f(x) add(x, 1)", "f"))
        assertEquals("Fn([Double] Bool)", shown("def: f(x) lt(x, 1.5)", "f"))
    }

    @Test
    fun `equality is not numeric`() {
        assertEquals("Bool", shown("def: f eq(\"a\", \"b\")", "f"))
        assertEquals("Bool", shown("def: f eq(nil, 1)", "f"))
    }
}
