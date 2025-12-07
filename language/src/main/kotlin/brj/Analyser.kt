package brj

private fun analyseListForm(form: ListForm): Expr {
    val els = form.els

    val first = els.firstOrNull() ?: TODO("empty list not supported yet")
    return when (first) {
        is SymbolForm -> when (first.name) {
            "def" -> StringExpr("def expr", form.loc)
            "let" -> StringExpr("let expr", form.loc)
            "if" -> StringExpr("if expr", form.loc)
            else -> StringExpr("call expr", form.loc)
        }

        else -> error("can't eval anything else")
    }
}

internal fun analyseForm(form: Form): Expr {
    return when (form) {
        is IntForm -> IntExpr(form.value, form.loc)
        is DoubleForm -> DoubleExpr(form.value, form.loc)
        is StringForm -> StringExpr(form.value, form.loc)
        is SymbolForm -> TODO("symbol form")
        is KeywordForm -> TODO("keyword form")

        is ListForm -> analyseListForm(form)
        is VectorForm -> VectorExpr(form.els.map { analyseForm(it) }, form.loc)
        is SetForm -> SetExpr(form.els.map { analyseForm(it) }, form.loc)
        is MapForm -> MapExpr(form.els.map { analyseForm(it) }, form.loc)
    }
}
