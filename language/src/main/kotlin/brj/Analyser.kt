package brj

private class ParseResult<R>(val forms: List<Form>, val res: R) {
    fun <T> then(parse: FormParser.() -> T): T {
        TODO()
    }
}

private class FormParser(private var forms: List<Form>) {
    fun expectForm(): Form {
        val ret = forms.firstOrNull() ?: TODO()
        forms = forms.drop(1)
        return ret
    }

    fun <R> maybe(parse: FormParser.() -> R?): R? {
        return try {
            this.parse()
        } catch (e: Exception) {
            null
        }
    }
}

private fun <R> parseForms(forms: List<Form>, parse: FormParser.() -> R): R {
    return parse(FormParser(forms))
}

internal fun analyseValueForm(form: Form) : ValueExpr = when (form) {
    is ListForm -> parseForms(form.forms) {
        TODO()
    }
    is IntForm -> IntExpr(form.int, form.loc)
    is BoolForm -> BoolExpr(form.bool, form.loc)
    is StringForm -> StringExpr(form.string, form.loc)
    is VectorForm -> VectorExpr(form.forms.map { analyseValueForm(it) }, form.loc)
    is SetForm -> SetExpr(form.forms.map { analyseValueForm(it) }, form.loc)
    is RecordForm -> TODO()
    is SymbolForm -> TODO()
}

