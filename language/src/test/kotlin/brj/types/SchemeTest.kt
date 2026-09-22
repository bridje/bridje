package brj.types

import brj.GlobalVar
import brj.analyser.*
import brj.runtime.sym
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SchemeTest {

    private val ns = "t".sym
    private fun lv(name: String, slot: Int = 0) = LocalVar(name.sym, slot)
    private fun fn(vararg params: LocalVar, body: ValueExpr) = FnExpr("f".sym, params.toList(), body, params.size, emptyList(), false)

    private fun identityScheme(): Scheme {
        val x = lv("x")
        return fn(x, body = LocalVarExpr(x)).typing().generalise()
    }

    // (fn (p x) (if p x 1)) generalised: its result carries the bound x ∨ Int.
    private fun xOrOneScheme(): Scheme {
        val p = lv("p", 1)
        val x = lv("x")
        return fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), IntExpr(1))).typing().generalise()
    }

    private fun ctxWith(vararg globals: Pair<GlobalVar, Scheme>) = globals.toMap().let { m -> TypeCtx(globalSchemes = { m[it] }) }
    private fun global(name: String) = GlobalVar(ns, name.sym, null)

    @Test
    fun `a scheme instantiates to fresh variables each time`() {
        val scheme = identityScheme()
        val (a, _) = scheme.instantiate()
        val (b, _) = scheme.instantiate()
        assertNotEquals(a, b)
        assertSame((a as FnType).paramTypes.single(), a.returnType)
    }

    @Test
    fun `a polymorphic global is used at two types in one body`() {
        val id = global("id")
        val ctx = ctxWith(id to identityScheme())
        // Each use instantiates afresh, so the two calls do not unify with each other …
        val intCall = CallExpr(GlobalVarExpr(id), listOf(IntExpr(1))).typing(ctx)
        val strCall = CallExpr(GlobalVarExpr(id), listOf(StringExpr("s"))).typing(ctx)
        assertEquals(IntType, intCall.type.positive(intCall.bounds, ctx))
        assertEquals(StrType, strCall.type.positive(strCall.bounds, ctx))
        // … while the results still meet at a join, where Int and Str are cross-kind.
        assertThrows<TypeCheckException> {
            VectorExpr(listOf(
                CallExpr(GlobalVarExpr(id), listOf(IntExpr(1))),
                CallExpr(GlobalVarExpr(id), listOf(StringExpr("s"))),
            )).typing(ctx)
        }
    }

    @Test
    fun `instantiation copies the scheme's bounds`() {
        val f = global("f")
        val ctx = ctxWith(f to xOrOneScheme())
        val ok = CallExpr(GlobalVarExpr(f), listOf(BoolExpr(true), IntExpr(2))).typing(ctx)
        assertEquals(IntType, ok.type.positive(ok.bounds, ctx))
        // x ∨ Int with x := Str is the cross-kind union, caught at the call.
        assertThrows<TypeCheckException> {
            CallExpr(GlobalVarExpr(f), listOf(BoolExpr(true), StringExpr("s"))).typing(ctx)
        }
    }

    @Test
    fun `generalising a typing with free locals is a bug, not a type`() {
        val x = lv("x")
        assertThrows<IllegalStateException> { LocalVarExpr(x).typing().generalise() }
    }

    @Test
    fun `a definition matching its declaration exports the declared type`() {
        val a = TypeVar()
        val declared = FnType(listOf(a), a)
        val exported = checkDeclared(identityScheme(), declared, TypeCtx.EMPTY)
        assertEquals(declared, exported.type)
    }

    @Test
    fun `a definition less general than its declaration is rejected`() {
        val x = lv("x")
        val constOne = fn(x, body = IntExpr(1)).typing().generalise()
        val a = TypeVar()
        assertThrows<TypeCheckException> { checkDeclared(constOne, FnType(listOf(a), a), TypeCtx.EMPTY) }
    }

    @Test
    fun `a declaration may narrow the definition`() {
        val exported = checkDeclared(identityScheme(), FnType(listOf(IntType), IntType), TypeCtx.EMPTY)
        assertEquals(FnType(listOf(IntType), IntType), exported.type)
    }

    @Test
    fun `a declaration the definition cannot meet is rejected`() {
        assertThrows<TypeCheckException> { checkDeclared(identityScheme(), FnType(listOf(IntType), StrType), TypeCtx.EMPTY) }
        assertThrows<TypeCheckException> { checkDeclared(identityScheme(), FnType(listOf(NullableType(IntType)), IntType), TypeCtx.EMPTY) }
    }

    @Test
    fun `withFx checks the bound value against the effect's declared type`() {
        val log = GlobalVar(ns, "log".sym, null, scheme = Scheme(FnType(listOf(StrType), NilType)))
        val msg = lv("msg")
        val fine = WithFxExpr(listOf(log to fn(msg, body = NilExpr())), IntExpr(1)).typing(TypeCtx.EMPTY)
        assertEquals(IntType, fine.type.positive(fine.bounds))
        assertThrows<TypeCheckException> {
            WithFxExpr(listOf(log to IntExpr(1)), IntExpr(1)).typing(TypeCtx.EMPTY)
        }
    }
}
