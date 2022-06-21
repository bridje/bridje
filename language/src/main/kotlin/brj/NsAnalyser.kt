package brj

import brj.runtime.BridjeContext
import brj.runtime.NsEnv
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.sym

private val IMPORTS = "imports".sym
private val ALIASES = "aliases".sym

internal interface AnalyserError

internal class DuplicateRecordKey(form: KeywordForm) : AnalyserError
internal class NsFormShouldBeList(form: Form) : AnalyserError
internal class NsShouldHaveAForm(form: Form) : AnalyserError
internal class ExpectingNsSymbol(form: Form) : AnalyserError
internal class ExpectingNsNameForm(form: Form) : AnalyserError
internal class ExpectingNsNameSymbol(form: Form) : AnalyserError
internal class ExpectingNsDepsRecord(form: Form) : AnalyserError
internal class UnknownNsDepsKey(form: KeywordForm) : AnalyserError
internal class ExtraFormsError(forms: List<Form>) : AnalyserError

internal class ThingAnalyser {
    val errs = mutableListOf<AnalyserError>()

    fun raise(e: AnalyserError) {
        errs.add(e)
    }

    fun bail(e: AnalyserError): Nothing {
        raise(e)
        TODO()
    }

    fun <R> R?.orBail(): R = this ?: TODO()
    fun <R> R?.orBail(h: () -> AnalyserError): R = this ?: bail(h())

    fun <R> R?.orRaise(h: () -> AnalyserError): R? {
        if (this == null) raise(h())
        return this
    }

    fun Form.isSym(sym: Symbol) = this is SymbolForm && this.sym == sym

    fun bailIfErrors() {
        if (errs.isNotEmpty()) TODO()
    }

    fun RecordForm.parseToMap(): Map<KeywordForm, Form>? =
        unbail {
            val recordForms = forms.toMutableList()
            val res = mutableMapOf<KeywordForm, Form>()

            while (recordForms.isNotEmpty()) {
                unbail {
                    val k = (recordForms.removeFirst() as? KeywordForm).orRaise { TODO() }
                    val v = recordForms.removeFirstOrNull()

                    if (k != null) {
                        if (v == null) bail(TODO())
                        if (res.containsKey(k)) bail(DuplicateRecordKey(k))
                        res[k] = v
                    }
                }
            }

            res
        }

    fun <R> unbail(f: ThingAnalyser.() -> R): R? {
        val inner = ThingAnalyser()
        val res = inner.run(f)

        return if (inner.errs.isNotEmpty()) {
            errs.addAll(inner.errs)
            null
        } else {
            res
        }
    }
}

private class NsDeps(
    var aliases: Map<Symbol, Symbol>? = null,
    var imports: Map<Symbol, Symbol>? = null
)

private fun ThingAnalyser.parseNsDeps(form: Form?): NsDeps {
    val deps = NsDeps()

    if (form != null) {
        (form as? RecordForm).orBail { ExpectingNsDepsRecord(form) }
            .parseToMap().orBail()
            .forEach { (k, v) ->
                unbail<Unit> {
                    when (k.sym) {
                        ALIASES -> TODO()
                        IMPORTS -> TODO()
                        else -> bail(UnknownNsDepsKey(k))
                    }
                }
            }
    }

    return deps
}

internal fun BridjeContext.analyseNs(form: Form): NsEnv =
    ThingAnalyser().run {
        val forms = (form as? ListForm)
            .orBail { NsFormShouldBeList(form) }
            .forms.toMutableList()

        forms.removeFirstOrNull().orBail { NsShouldHaveAForm(form) }
            .run { if (!isSym("ns".sym)) bail(ExpectingNsSymbol(this)) }

        val nameForm = forms.removeFirstOrNull().orBail { ExpectingNsNameForm(form) }

        val name = unbail {
            if (nameForm is SymbolForm) nameForm.sym
            else bail(ExpectingNsNameSymbol(nameForm))
        }

        val deps = parseNsDeps(forms.removeFirstOrNull())

        if (forms.isNotEmpty()) bail(ExtraFormsError(forms))

        bailIfErrors()
        NsEnv(name!!, deps.aliases ?: emptyMap(), deps.imports ?: emptyMap())
}