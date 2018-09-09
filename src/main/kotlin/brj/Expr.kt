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

    private data class Analyser constructor(val loopLocals: List<LocalVar>?) {
        fun analyseValueForm(form: Form): Expr =
            when (form) {
                is Form.BooleanForm -> BooleanExpr(form.bool)
                is Form.StringForm -> StringExpr(form.string)
                is Form.IntForm -> IntExpr(form.int)
                is Form.BigIntForm -> BigIntExpr(form.bigInt)
                is Form.FloatForm -> FloatExpr(form.float)
                is Form.BigFloatForm -> BigFloatExpr(form.bigFloat)
                is Form.SymbolForm -> TODO()
                is Form.KeywordForm -> TODO()
                is Form.ListForm -> TODO()
                is Form.VectorForm -> VectorExpr(form.forms.map(this.copy(loopLocals = null)::analyseValueForm))
                is Form.SetForm -> SetExpr(form.forms.map(this.copy(loopLocals = null)::analyseValueForm))
                is Form.RecordForm -> TODO()
                is Form.QuoteForm -> TODO()
                is Form.UnquoteForm -> TODO()
                is Form.UnquoteSplicingForm -> TODO()
            }
    }

    companion object {
        fun analyseValueForm(form: Form): Expr = Analyser(loopLocals = null).analyseValueForm(form)
    }
}