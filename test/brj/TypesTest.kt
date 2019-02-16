package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import org.junit.jupiter.api.Test

internal class TypesTest {

    @Test
    internal fun `test variant types`() {
        val tv = TypeVarType()
        val fooKey = VariantKey(mkQSym(":user/Foo"), listOf(tv), listOf(tv))

        println(valueExprType(CallExpr(GlobalVarExpr(VariantKeyVar(fooKey, null)), listOf(IntExpr(5)))))
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
            null))))
    }
}
