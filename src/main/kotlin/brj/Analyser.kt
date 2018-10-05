package brj

import brj.Analyser.AnalyserError.ExpectedForm
import brj.Analyser.AnalyserError.UnexpectedForms

typealias FormsAnalyser<R> = (Analyser.AnalyserState) -> R

@Suppress("NestedLambdaShadowedImplicitParameter")
object Analyser {

    val DEF = Symbol("def")
    val IF = Symbol("if")
    val FN = Symbol("fn")
    val LET = Symbol("let")
    val DO = Symbol("do")

    sealed class AnalyserError : Exception() {
        object ExpectedForm : AnalyserError()
        data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
        data class ResolutionError(val sym: Symbol) : AnalyserError()
        object InvalidDefDefinition : AnalyserError()
    }

    data class AnalyserState(var forms: List<Form>) {
        fun <R> zeroOrMore(a: FormsAnalyser<R>): List<R> {
            val ret: MutableList<R> = mutableListOf()

            while (true) {
                val res = maybe(a)

                if (res != null) {
                    ret += res
                } else {
                    return ret
                }
            }
        }

        fun expectEnd() {
            if (forms.isNotEmpty()) {
                throw UnexpectedForms(forms)
            }
        }

        inline fun <reified F : Form> expectForm(): F =
            if (forms.isNotEmpty()) {
                val firstForm = forms.first() as? F ?: throw ExpectedForm
                forms = forms.drop(1)
                firstForm
            } else {
                throw ExpectedForm
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

        inline fun <reified F : Form, R> nested(f: (F) -> List<Form>, a: FormsAnalyser<R>): R =
            a(AnalyserState(f(expectForm())))
    }

}

