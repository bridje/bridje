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

    private val serverStateCtor = BridjeTagConstructor("ServerState", listOf(key("log")))
    private val serverState = TagRef(ns, "ServerState".sym)
    private val ctx = TypeCtx(
        keyTypes = { mapOf("dirty" to BoolType, "log" to VectorType(IntType), "a" to IntType)[it.name.name]?.let(::KeyType) },
        tagsByValue = mapOf(serverStateCtor to TagInfo(serverState, null, emptyList(), Payload.Record(keys("log")))),
    )

    private fun getter(name: String) = GlobalVarExpr(GlobalVar(ns, name.sym, BridjeKey(ns, name.sym)))
    private fun get(name: String, target: ValueExpr) = CallExpr(getter(name), listOf(target))
    private fun record(vararg fields: Pair<String, ValueExpr>) = RecordExpr(fields.map { (k, v) -> key(k) to v })
    private fun with(r: ValueExpr, vararg fields: Pair<String, ValueExpr>) = RecordUpdateExpr(r, fields.map { (k, v) -> key(k) to v })

    private fun ValueExpr.shown(): String = typing(ctx).generalise().simplify(ctx).toString()

    // Bounds as the solver leaves them, without inferring anything.
    private fun shown(type: Type, vararg constraints: Pair<Type, Type>): String =
        Scheme(type, emptyMap<TypeVar, Bounds>().constrainAll(constraints.toList(), ctx)).simplify(ctx).toString()

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
    fun `a variable joined with a primitive is that primitive`() {
        // (fn (p x) (if p x 1)): x ∨ Int is only defined where x is an Int.
        val p = lv("p")
        val x = lv("x", 1)
        assertEquals("Fn([Bool, Int] Int)", fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), IntExpr(1))).shown())
    }

    @Test
    fun `a variable compared with a literal is the literal's type`() {
        // (fn (n) (if (gt n 0) 1 (if (lt n 0) 2 3))): n and 0 join in gt's variable, so n is an Int.
        val t = freshVar()
        val cmp = brj.GlobalVar("t".sym, "gt".sym, Any(), scheme = Scheme(FnType(listOf(t, t), BoolType)))
        val n = lv("n")
        fun test() = CallExpr(GlobalVarExpr(cmp), listOf(LocalVarExpr(n), IntExpr(0)))
        val body = IfExpr(test(), IntExpr(1), IfExpr(test(), IntExpr(2), IntExpr(3)))
        assertEquals("Fn([Int] Int)", fn(n, body = body).shown())
    }

    @Test
    fun `a bound the type cannot say is an isa constraint`() {
        // (fn (p x) (if p x [1])): x ∨ [Int] depends on x, so the lower bound stays.
        val p = lv("p")
        val x = lv("x", 1)
        assertEquals("[a, isa([Int], a)] Fn([Bool, a] a)", fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), VectorExpr(listOf(IntExpr(1))))).shown())
    }

    @Test
    fun `the with-or-input idiom is a pass-through over records`() {
        // (fn (p r) (if p (with r .dirty true) r)): r is a record, and the result is r.
        val p = lv("p")
        val r = lv("r", 1)
        val body = IfExpr(LocalVarExpr(p), with(LocalVarExpr(r), "dirty" to BoolExpr(true)), LocalVarExpr(r))
        assertEquals("[a] Fn([Bool, {& a}] a)", fn(p, r, body = body).shown())
    }

    @Test
    fun `a key demanded of a variable is written on it`() {
        val p = lv("p")
        val s = lv("s", 1)
        val body = DoExpr(
            listOf(get("log", LocalVarExpr(s))),
            IfExpr(LocalVarExpr(p), with(LocalVarExpr(s), "dirty" to BoolExpr(true)), LocalVarExpr(s)),
        )
        val shown = fn(p, s, body = body).shown()
        assertEquals("[a] Fn([Bool, {.log & a}] a)", shown.replace("t/.", "."))
    }

    @Test
    fun `with on a variable that already carries the key is the variable`() {
        // (fn (r) (with r .a (.a r))): reading .a demands it of r, so the result's {.a & a} is r.
        val r = lv("r")
        val shown = fn(r, body = with(LocalVarExpr(r), "a" to get("a", LocalVarExpr(r)))).shown()
        assertEquals("[a] Fn([{.a & a}] a)", shown.replace("t/.", "."))
    }

    @Test
    fun `with on a variable puts the key on it`() {
        val r = lv("r")
        val shown = fn(r, body = with(LocalVarExpr(r), "a" to IntExpr(1))).shown()
        assertEquals("[a] Fn([{& a}] {.a & a})", shown.replace("t/.", "."))
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
    fun `an ifLet value is nullable, and its binding is not`() {
        // (fn (x) (ifLet [y x] y 0))
        val x = lv("x")
        val y = lv("y", 1)
        val body = IfLetExpr(y, LocalVarExpr(x), LocalVarExpr(y), IntExpr(0))
        assertEquals("Fn([Int?] Int)", fn(x, body = body).shown())
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

    @Test
    fun `a variable only produced is what flows into it`() {
        val v = freshVar()
        assertEquals("Fn([] Int)", shown(FnType(emptyList(), v), IntType to v))
    }

    @Test
    fun `a variable only consumed is what it is demanded to be`() {
        val v = freshVar()
        assertEquals("Fn([Str] Bool)", shown(FnType(listOf(v), BoolType), v to StrType))
    }

    @Test
    fun `a variable passed through is one quantified variable`() {
        val (a, b) = List(2) { freshVar() }
        assertEquals("[a] Fn([a] a)", shown(FnType(listOf(a), b), a to b))
    }

    @Test
    fun `a key demanded of a variable passed through is written on it`() {
        val (a, b) = List(2) { freshVar() }
        assertEquals("[a] Fn([{t/.a & a}] a)", shown(FnType(listOf(a), b), a to b, a to RecordType(setOf(key("a")))))
    }

    // Either argument may be returned, so the two are one variable at the type's return and apart at its parameters.
    @Test
    fun `two variables flowing to one result are one variable`() {
        val (a, b, c) = List(3) { freshVar() }
        assertEquals("[a] Fn([a, a] a)", shown(FnType(listOf(a, b), c), a to c, b to c))
    }

    // As above, but only a is demanded to be an Iterable: merging a with b, or into the result, would demand it of
    // b as well. b and the result are one variable.
    @Test
    fun `a bound on one of two variables is not put on the other`() {
        val (a, b, c) = List(3) { freshVar() }
        assertEquals("[a, b, isa(a, Iterable(Int)), isa(a, b)] Fn([a, b] b)", shown(FnType(listOf(a, b), c), a to c, b to c, a to IterableType(IntType)))
    }

    // x holds an Int and y a Str, and z nothing concrete, all three between p and q. z agrees with each alone,
    // but merging all three would keep one concrete bound and drop the other.
    @Test
    fun `merging co-occurring variables keeps every concrete bound`() {
        val (p, q, x, y, z) = List(5) { TypeVar() }
        val env: BoundEnv = mapOf(
            p to Bounds(upper = UpperBound(tvs = mapOf(x to false, y to false, z to false))),
            q to Bounds(lower = LowerBound(tvs = setOf(x, y, z))),
            x to Bounds(LowerBound(IntType, setOf(p)), UpperBound(tvs = mapOf(q to false))),
            y to Bounds(LowerBound(StrType, setOf(p)), UpperBound(tvs = mapOf(q to false))),
            z to Bounds(LowerBound(tvs = setOf(p)), UpperBound(tvs = mapOf(q to false))),
        )
        val shown = Scheme(FnType(listOf(p.type()), q.type()), env).simplify().toString()
        assertTrue("Int" in shown && "Str" in shown, shown)
    }
}
