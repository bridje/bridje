package brj

import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import java.math.BigDecimal
import java.math.BigInteger

object GraalEmitter {

    data class BoolNode(val lang: TruffleLanguage<out Any>, val boolean: Boolean): RootNode(lang) {
        override fun execute(frame: VirtualFrame): Boolean = boolean
    }

    data class StringNode(val lang: TruffleLanguage<out Any>, val string: String): RootNode(lang) {
        override fun execute(frame: VirtualFrame): String = string
    }

    data class IntNode(val lang: TruffleLanguage<out Any>, val int: Long): RootNode(lang) {
        override fun execute(frame: VirtualFrame?): Long = int
    }

    data class BigIntNode(val lang: TruffleLanguage<out Any>, val bigInt: BigInteger): RootNode(lang) {
        override fun execute(frame: VirtualFrame?): BigInteger = bigInt
    }

    data class FloatNode(val lang: TruffleLanguage<out Any>, val float: Double): RootNode(lang) {
        override fun execute(frame: VirtualFrame?): Double = float
    }

    data class BigFloatNode(val lang: TruffleLanguage<out Any>, val bigDec: BigDecimal): RootNode(lang) {
        override fun execute(frame: VirtualFrame?): BigDecimal = bigDec
    }

    fun emitValueExpr(lang: TruffleLanguage<out Any>, expr: ValueExpr): RootNode =
        when (expr) {
            is BooleanExpr -> BoolNode(lang, expr.boolean)
            is StringExpr -> StringNode(lang, expr.string)
            is IntExpr -> IntNode(lang, expr.int)
            is BigIntExpr -> BigIntNode(lang, expr.bigInt)
            is FloatExpr -> FloatNode(lang, expr.float)
            is BigFloatExpr -> BigFloatNode(lang, expr.bigFloat)
            is VectorExpr -> TODO()
            is SetExpr -> TODO()
        }
}