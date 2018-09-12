package brj

data class Analyser(val loopLocals: List<Expr.LocalVar>?) {
    fun analyseValueForm(form: Form): Expr =
        when (form) {
            is Form.BooleanForm -> Expr.BooleanExpr(form.bool)
            is Form.StringForm -> Expr.StringExpr(form.string)
            is Form.IntForm -> Expr.IntExpr(form.int)
            is Form.BigIntForm -> Expr.BigIntExpr(form.bigInt)
            is Form.FloatForm -> Expr.FloatExpr(form.float)
            is Form.BigFloatForm -> Expr.BigFloatExpr(form.bigFloat)
            is Form.SymbolForm -> TODO()
            is Form.KeywordForm -> TODO()
            is Form.ListForm -> TODO()
            is Form.VectorForm -> Expr.VectorExpr(form.forms.map(this.copy(loopLocals = null)::analyseValueForm))
            is Form.SetForm -> Expr.SetExpr(form.forms.map(this.copy(loopLocals = null)::analyseValueForm))
            is Form.RecordForm -> TODO()
            is Form.QuoteForm -> TODO()
            is Form.UnquoteForm -> TODO()
            is Form.UnquoteSplicingForm -> TODO()
        }

    companion object {
        fun analyseValueForm(form: Form): Expr = Analyser(loopLocals = null).analyseValueForm(form)
    }
}
