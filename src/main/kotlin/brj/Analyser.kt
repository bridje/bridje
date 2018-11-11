package brj

typealias FormsAnalyser<R> = (AnalyserState) -> R

sealed class AnalyserError : Exception() {
    object ExpectedForm : AnalyserError()
    data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
    data class ResolutionError(val ident: Ident) : AnalyserError()
    object ExpectedSymbol : AnalyserError()
}

data class AnalyserState(var forms: List<Form>) {
    fun <R> varargs(a: FormsAnalyser<R>): List<R> {
        val ret: MutableList<R> = mutableListOf()

        while (forms.isNotEmpty()) {
            ret += a(this)
        }

        return ret
    }

    fun expectEnd() {
        if (forms.isNotEmpty()) {
            throw AnalyserError.UnexpectedForms(forms)
        }
    }

    inline fun <reified F : Form> expectForm(): F =
        if (forms.isNotEmpty()) {
            val firstForm = forms.first() as? F ?: throw AnalyserError.ExpectedForm
            forms = forms.drop(1)
            firstForm
        } else {
            throw AnalyserError.ExpectedForm
        }

    fun <R> maybe(a: FormsAnalyser<R?>): R? =
        try {
            val newState = copy()
            val res = a(newState)

            if (res != null) {
                forms = newState.forms
            }

            res
        } catch (e: AnalyserError) {
            null
        }

    fun <R> or(vararg analysers: FormsAnalyser<R>): R? {
        for (a in analysers) {
            val res = maybe(a)
            if (res != null) {
                return res
            }
        }

        return null
    }

    fun <R> nested(forms: List<Form>, a: FormsAnalyser<R>): R = a(AnalyserState(forms))

    inline fun <reified F : Form, R> nested(f: (F) -> List<Form>, noinline a: FormsAnalyser<R>): R = nested(f(expectForm()), a)

    fun expectSym(expectedSym: Symbol) {
        val actualSym = maybe { it.expectForm<SymbolForm>().sym }
        if (expectedSym != actualSym) throw AnalyserError.ExpectedSymbol
    }
}

class LocalVar(val s: Symbol)