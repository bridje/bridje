package brj

import java.math.BigDecimal
import java.math.BigInteger

sealed class Expr {

    class LocalVar(val s: Symbol)

    sealed class ValueExpr : Expr() {
        data class BooleanExpr(val boolean: Boolean) : ValueExpr()
        data class StringExpr(val string: String) : ValueExpr()
        data class IntExpr(val int: Long) : ValueExpr()
        data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
        data class FloatExpr(val float: Double) : ValueExpr()
        data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()

        data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()
        data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()

        data class CallExpr(val f: ValueExpr, val args: List<ValueExpr>) : ValueExpr()

        data class FnExpr(val fnName: Symbol?, val params: List<LocalVar>, val expr: ValueExpr) : ValueExpr()
        data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()
        data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

        data class Binding(val localVar: LocalVar, val expr: ValueExpr)
        data class LetExpr(val bindings: List<Binding>, val expr: ValueExpr) : ValueExpr()

        data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()
    }
}