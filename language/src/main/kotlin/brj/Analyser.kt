package brj

internal fun analyseValueForm(form: Form) : ValueExpr = when (form) {
    is ListForm -> TODO()
    is IntForm -> IntExpr(form.int, form.loc)
    is BoolForm -> BoolExpr(form.bool, form.loc)
    is StringForm -> StringExpr(form.string, form.loc)
    is VectorForm -> VectorExpr(form.forms.map { analyseValueForm(it) }, form.loc)
    is SetForm -> SetExpr(form.forms.map { analyseValueForm(it) }, form.loc)
    is RecordForm -> TODO()
    is SymbolForm -> TODO()
}

