package brj

internal fun analyseForm(form: Form): Expr {
    return when (form) {
        is IntForm -> IntExpr(form.value, form.loc)
        is DoubleForm -> DoubleExpr(form.value, form.loc)
        is StringForm -> StringExpr(form.value, form.loc)

        is ListForm -> TODO("ListForm")
        is VectorForm -> VectorExpr(form.els.map { analyseForm(it) }, form.loc)
        is SetForm -> SetExpr(form.els.map { analyseForm(it) }, form.loc)
        is MapForm -> MapExpr(form.els.map { analyseForm(it) }, form.loc)
    }
}
