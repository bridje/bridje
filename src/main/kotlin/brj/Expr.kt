package brj

import java.math.BigDecimal
import java.math.BigInteger

sealed class Expr {

    data class LocalVar(val s: String)

    sealed class ValueExpr : Expr() {
        data class BooleanExpr(val boolean: Boolean) : ValueExpr()
        data class StringExpr(val string: String) : ValueExpr()
        data class IntExpr(val int: Long) : ValueExpr()
        data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
        data class FloatExpr(val float: Double) : ValueExpr()
        data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()
        data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()
        data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()
    }
}