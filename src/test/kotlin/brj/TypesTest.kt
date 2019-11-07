package brj

import brj.analyser.*
import brj.runtime.QSymbol.Companion.mkQSym
import brj.runtime.RecordKey
import brj.runtime.RecordKeyVar
import brj.runtime.Symbol.Companion.mkSym
import brj.runtime.VariantKey
import brj.runtime.VariantKeyVar
import brj.types.TypeVarType
import org.junit.jupiter.api.Test
import brj.types.valueExprType

internal class TypesTest {

    private val dummyVar = object : Any() {}

    @Test
    internal fun `test record types`() {
        val tv = TypeVarType()
        val fooKey = RecordKey(mkQSym(":user/foo"), listOf(tv), tv)

        println(valueExprType(RecordExpr(listOf(RecordEntry(fooKey, IntExpr(5)))), null))

        val localVar = LocalVar(mkSym("r"))
        val effectLocal = LocalVar(mkSym("fx"))

        val type = valueExprType(
            FnExpr(null, listOf(localVar),
                CallExpr(
                    GlobalVarExpr(RecordKeyVar(fooKey, dummyVar), effectLocal), listOf(LocalVarExpr(localVar)),
                    effectLocal)),
            null).monoType

        println(type)
    }

    @Test
    internal fun `test variant types`() {
        val tv = TypeVarType()
        val fooKey = VariantKey(mkQSym(":user/Foo"), listOf(tv), listOf(tv))
        val effectLocal = LocalVar(mkSym("fx"))

        println(valueExprType(CallExpr(GlobalVarExpr(VariantKeyVar(fooKey, dummyVar), effectLocal), listOf(IntExpr(5)), effectLocal), null))
    }

    @Test
    internal fun `test case expr`() {
        val tv = TypeVarType()
        val fooKey = VariantKey(mkQSym(":user/Foo"), listOf(tv), listOf(tv))
        val barKey = VariantKey(mkQSym(":user/Bar"), emptyList(), emptyList())

        val paramVar = LocalVar(mkSym("foo"))
        val bindingVar = LocalVar(mkSym("a"))

        println(valueExprType(FnExpr(null, listOf(paramVar), CaseExpr(
            LocalVarExpr(paramVar),
            listOf(
                CaseClause(fooKey, listOf(bindingVar), LocalVarExpr(bindingVar)),
                CaseClause(barKey, emptyList(), FloatExpr(5.3))),
            null)), null))
    }
}
