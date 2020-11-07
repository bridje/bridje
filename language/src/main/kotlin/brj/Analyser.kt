package brj

import brj.Symbol.Companion.symbol


private fun FormParser.parseValueExpr() = analyseValueForm(expectForm())

private val DO = symbol("do")
private val IF = symbol("if")

private fun FormParser.parseDo(form: Form): ValueExpr {
    val exprs = rest(FormParser::parseValueExpr)
    if (exprs.isEmpty()) TODO()
    return DoExpr(exprs.dropLast(1), exprs.last(), form.loc)
}

private fun FormParser.parseIf(form: Form): ValueExpr {
    val expr = IfExpr(parseValueExpr(), parseValueExpr(), parseValueExpr(), form.loc)
    expectEnd()
    return expr
}

internal fun analyseValueForm(form: Form): ValueExpr = when (form) {
    is ListForm -> parseForms(form.forms) {
        or({
            when (val sym = expectSymbol()) {
                DO -> parseDo(form)
                IF -> parseIf(form)
                else -> TODO()
            }
        }) ?: TODO()
    }

    is IntForm -> IntExpr(form.int, form.loc)
    is BoolForm -> BoolExpr(form.bool, form.loc)
    is StringForm -> StringExpr(form.string, form.loc)
    is VectorForm -> VectorExpr(form.forms.map { analyseValueForm(it) }, form.loc)
    is SetForm -> SetExpr(form.forms.map { analyseValueForm(it) }, form.loc)
    is RecordForm -> TODO()
    is SymbolForm -> TODO()
}

