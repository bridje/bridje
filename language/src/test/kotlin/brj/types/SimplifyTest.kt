package brj.types

import brj.GlobalVar
import brj.analyser.*
import brj.runtime.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SimplifyTest {

    private val ns = "t".sym
    private fun key(name: String) = QSymbol(ns, name.sym)
    private fun keys(vararg names: String) = names.map { key(it) }.toSet()
    private fun lv(name: String, slot: Int = 0) = LocalVar(name.sym, slot)
    private fun fn(vararg params: LocalVar, body: ValueExpr) = FnExpr("f".sym, params.toList(), body, params.size, emptyList(), false)

    private val serverStateCtor = BridjeTagConstructor("ServerState", 1, listOf(key("log")))
    private val serverState = TagRef(ns, "ServerState".sym)
    private val ctx = TypeCtx(
        keyTypes = { mapOf("dirty" to BoolType, "log" to VectorType(IntType), "a" to IntType)[it.name.name] },
        tagsByValue = mapOf(serverStateCtor to TagInfo(serverState, null, 0, Payload.Record(keys("log")))),
    )

    private fun getter(name: String) = GlobalVarExpr(GlobalVar(ns, name.sym, BridjeKey(ns, name.sym)))
    private fun get(name: String, target: ValueExpr) = CallExpr(getter(name), listOf(target))
    private fun record(vararg fields: Pair<String, ValueExpr>) = RecordExpr(fields.map { (k, v) -> key(k) to v })
    private fun with(r: ValueExpr, vararg fields: Pair<String, ValueExpr>) = RecordUpdateExpr(r, fields.map { (k, v) -> key(k) to v })

    private fun ValueExpr.shown(): String = typing(ctx).generalise().simplify(ctx).toString()

    @Test
    fun `a monomorphic type shows as itself`() {
        assertEquals("Int", IntExpr(1).shown())
        assertEquals("Fn([] Int)", fn(body = IntExpr(1)).shown())
    }

    @Test
    fun `identity is one quantified variable`() {
        val x = lv("x")
        assertEquals("[a] Fn([a] a)", fn(x, body = LocalVarExpr(x)).shown())
    }

    @Test
    fun `a negative-only variable collapses to its demand`() {
        val x = lv("x")
        assertEquals("Fn([Bool] Int)", fn(x, body = IfExpr(LocalVarExpr(x), IntExpr(1), IntExpr(2))).shown())
    }

    @Test
    fun `a variable joined with a literal keeps the literal as a lower bound`() {
        // (fn (p x) (if p x 1)) : ∀a. Int ≤ a ⇒ Bool → a → a
        val p = lv("p")
        val x = lv("x", 1)
        assertEquals("[a, ^(Int, a)] Fn([Bool, a] a)", fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), IntExpr(1))).shown())
    }

    @Test
    fun `the with-or-input idiom is a pass-through over records`() {
        // (fn (p r) (if p (with r .dirty true) r)) : ∀a ≤ {}. Bool → a → a
        val p = lv("p")
        val r = lv("r", 1)
        val body = IfExpr(LocalVarExpr(p), with(LocalVarExpr(r), "dirty" to BoolExpr(true)), LocalVarExpr(r))
        assertEquals("[a, ^(a, {})] Fn([Bool, a] a)", fn(p, r, body = body).shown())
    }

    @Test
    fun `a nominal demand renders as an upper bound`() {
        val p = lv("p")
        val s = lv("s", 1)
        val body = DoExpr(
            listOf(get("log", LocalVarExpr(s))),
            IfExpr(LocalVarExpr(p), with(LocalVarExpr(s), "dirty" to BoolExpr(true)), LocalVarExpr(s)),
        )
        val shown = fn(p, s, body = body).shown()
        assertEquals("[a, ^(a, {.log})] Fn([Bool, a] a)", shown.replace("{t/.log}", "{.log}"))
    }

    @Test
    fun `with on a variable whose bound already has the key keeps the meet for now`() {
        // (fn (r) (with r .a (.a r))): reading .a demands it of one use of r, with makes the other a
        // record. The uses meet in the parameter, so the result's meet with {a} restates the bound.
        // Folding it is simplifier work still to do.
        val r = lv("r")
        val shown = fn(r, body = with(LocalVarExpr(r), "a" to get("a", LocalVarExpr(r)))).shown()
        assertEquals("[a, ^(a, {.a})] Fn([a] a{.a})", shown.replace("{t/.a}", "{.a}"))
    }

    @Test
    fun `with on a variable without the key renders the promotion`() {
        val r = lv("r")
        val shown = fn(r, body = with(LocalVarExpr(r), "a" to IntExpr(1))).shown()
        assertEquals("[a, ^(a, {})] Fn([a] a{.a})", shown.replace("{t/.a}", "{.a}"))
    }

    @Test
    fun `a positive-only variable collapses to what flows into it`() {
        val p = lv("p")
        assertEquals("Fn([Bool] Int?)", fn(p, body = IfExpr(LocalVarExpr(p), IntExpr(1), NilExpr())).shown())
        assertEquals("[Int]", VectorExpr(listOf(IntExpr(1), IntExpr(2))).shown())
    }

    @Test
    fun `a higher-order argument keeps its own variables`() {
        val f = lv("f")
        assertEquals("[a] Fn([Fn([Int] a)] a)", fn(f, body = CallExpr(LocalVarExpr(f), listOf(IntExpr(1)))).shown())
    }

    @Test
    fun `an unconstrained variable stays quantified rather than becoming Nothing`() {
        val x = lv("x")
        val y = lv("y", 1)
        assertEquals("[a, b] Fn([a, b] a)", fn(x, y, body = LocalVarExpr(x)).shown())
    }

    @Test
    fun `a variable or nil is that variable, nullable`() {
        val p = lv("p")
        val x = lv("x", 1)
        assertEquals("[a] Fn([Bool, a] a?)", fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), NilExpr())).shown())
    }

    @Test
    fun `a value the checker knows nothing about adds nothing to a join`() {
        val p = lv("p")
        val x = lv("x", 1)
        assertEquals("[a] Fn([Bool, a] a)", fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), ErrorValueExpr("unknown"))).shown())
        // and on its own it is Nothing: nothing is known to flow out.
        assertEquals("Fn([] Nothing)", fn(body = ErrorValueExpr("unknown")).shown())
    }

    @Test
    fun `the results of two calls joined are one variable`() {
        val p = lv("p")
        val f = lv("f", 1)
        val g = lv("g", 2)
        val x = lv("x", 3)
        val body = IfExpr(LocalVarExpr(p), CallExpr(LocalVarExpr(f), listOf(LocalVarExpr(x))), CallExpr(LocalVarExpr(g), listOf(LocalVarExpr(x))))
        assertEquals("[a, b] Fn([Bool, Fn([a] b), Fn([a] b), a] b)", fn(p, f, g, x, body = body).shown())
    }

    @Test
    fun `a type error carries the constraint it was serving`() {
        val x = lv("x")
        val e = assertThrows<TypeCheckException> {
            CallExpr(fn(x, body = IfExpr(LocalVarExpr(x), IntExpr(1), IntExpr(2))), listOf(StringExpr("s"))).typing(ctx)
        }
        assertNotNull(e.provenance)
        assertTrue(e.message!!.contains("while checking"), e.message)
        assertTrue(e.message!!.startsWith("Str is not a subtype of Bool"), e.message)
    }
}
