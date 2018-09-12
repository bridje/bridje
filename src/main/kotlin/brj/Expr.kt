package brj

import java.math.BigDecimal
import java.math.BigInteger

sealed class Expr {
    data class BooleanExpr(val boolean: Boolean) : Expr()
    data class StringExpr(val string: String) : Expr()
    data class IntExpr(val int: Long) : Expr()
    data class BigIntExpr(val bigInt: BigInteger) : Expr()
    data class FloatExpr(val float: Double) : Expr()
    data class BigFloatExpr(val bigFloat: BigDecimal) : Expr()

    data class VectorExpr(val exprs: List<Expr>) : Expr()
    data class SetExpr(val exprs: List<Expr>) : Expr()

    data class LocalVar(val s: String)
}