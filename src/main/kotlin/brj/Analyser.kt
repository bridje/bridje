package brj

import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*

data class Analyser(val loopLocals: List<Expr.LocalVar>?) {
    fun analyseValueForm(form: Form): ValueExpr =
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

    companion object {
        fun analyseValueForm(form: Form): ValueExpr = Analyser(loopLocals = null).analyseValueForm(form)
    }
}
