package brj.types

import brj.runtime.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SimplifyTest {

    private val ns = "t".sym
    private fun key(name: String) = QSymbol(ns, name.sym)
    private val ctx = TypeCtx(keyTypes = { mapOf("a" to IntType)[it.name.name]?.let(::KeyType) })

    // Bounds as the solver leaves them, without inferring anything.
    private fun shown(type: Type, vararg constraints: Pair<Type, Type>): String =
        Scheme(type, emptyMap<TypeVar, Bounds>().constrainAll(constraints.toList(), ctx)).simplify(ctx).toString()

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
