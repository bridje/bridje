package brj.analyser

import brj.*
import brj.analyser.ParseError.ExpectedIdent
import com.oracle.truffle.api.source.SourceSection

internal typealias FormsParser<R> = (ParserState) -> R

sealed class ParseError : Exception() {
    object ExpectedForm : ParseError()
    data class UnexpectedForms(val forms: List<Form>) : ParseError()
    object ExpectedSymbol : ParseError()
    object ExpectedIdent : ParseError()
}

internal data class ParserState(var forms: List<Form>, val outerLoc: SourceSection? = null) {
    fun <R> many(a: FormsParser<R?>): List<R> {
        val ret: MutableList<R> = mutableListOf()

        while (forms.isNotEmpty()) {
            val res = a(this) ?: return ret
            ret += res
        }

        return ret
    }

    fun consume(): List<Form> {
        val ret = forms
        forms = emptyList()
        return ret
    }

    fun <R> varargs(a: FormsParser<R>): List<R> {
        val ret: MutableList<R> = mutableListOf()

        while (forms.isNotEmpty()) {
            ret += a(this)
        }

        return ret
    }

    fun expectEnd() {
        if (forms.isNotEmpty()) {
            throw ParseError.UnexpectedForms(forms)
        }
    }

    inline fun <reified F : Form> expectForm(): F =
        if (forms.isNotEmpty()) {
            val firstForm = forms.first() as? F ?: throw ParseError.ExpectedForm
            forms = forms.drop(1)
            firstForm
        } else {
            throw ParseError.ExpectedForm
        }

    fun <R> maybe(a: FormsParser<R?>): R? =
        try {
            val newState = copy()
            val res = a(newState)

            if (res != null) {
                forms = newState.forms
            }

            res
        } catch (e: ParseError) {
            null
        }

    fun <R> or(vararg analysers: FormsParser<R?>): R? {
        for (a in analysers) {
            val res = a(this)
            if (res != null) {
                return res
            }
        }

        return null
    }

    fun <R> nested(forms: List<Form>, a: FormsParser<R>): R = a(ParserState(forms))

    inline fun <reified F : Form, R> nested(f: (F) -> List<Form>, noinline a: FormsParser<R>): R = nested(f(expectForm()), a)
    inline fun <reified F : Form> nested(f: (F) -> List<Form>): ParserState = nested(f(expectForm())) { it }

    fun expectSym() = expectForm<SymbolForm>().sym

    fun expectSym(vararg kinds: SymbolKind): Symbol {
        val sym = expectSym()
        if (!kinds.contains(sym.symbolKind)) throw ParseError.ExpectedSymbol else return sym
    }

    fun expectSym(expectedSym: Symbol): Symbol {
        val actualSym = expectSym()
        if (expectedSym == actualSym) return expectedSym else throw ParseError.ExpectedSymbol
    }

    fun expectIdent(): Ident {
        val form = expectForm<Form>()
        return when (form) {
            is SymbolForm -> form.sym
            is QSymbolForm -> form.sym
            else -> throw ExpectedIdent
        }
    }

    fun expectIdent(vararg kinds: SymbolKind): Ident {
        val ident = expectIdent()
        if (!kinds.contains(ident.symbolKind)) TODO() else return ident
    }
}

