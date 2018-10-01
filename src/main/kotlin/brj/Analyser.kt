package brj

import brj.Analyser.AnalyserError.*
import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import brj.Form.*

typealias FormsAnalyser<R> = (Analyser.AnalyserCtx.AnalyserState) -> R

object Analyser {

    sealed class AnalyserError : Exception() {
        object ExpectedForm : AnalyserError()
        data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
    }

    data class AnalyserCtx(val loopLocals: List<LocalVar>? = null) {

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

            inline fun <reified F : Form, R> expectForm(f: (F) -> R): R =
                if (forms.isNotEmpty()) {
                    val firstForm = forms.first()
                    val r = f(firstForm as? F ?: throw ExpectedForm)
                    forms = forms.drop(1)
                    r
                } else {
                    throw ExpectedForm
                }

            fun <R> maybe(a: FormsAnalyser<R>): R? =
                try {
                    val newState = copy()
                    val res = a(newState)
                    forms = newState.forms
                    res
                } catch (e: AnalyserError) {
                    null
                }

            inline fun <reified F : Form, R> nested(f: (F) -> List<Form>, a: FormsAnalyser<R>): R =
                expectForm<F, R> { form -> a(AnalyserState(f(form))) }
        }

        private val ifAnalyser: FormsAnalyser<ValueExpr> = {
            val predExpr = exprAnalyser(it)
            val thenExpr = exprAnalyser(it)
            val elseExpr = exprAnalyser(it)
            it.expectEnd()
            IfExpr(predExpr, thenExpr, elseExpr)
        }

        private val fnAnalyser: FormsAnalyser<ValueExpr> = {
            val fnName = it.maybe { it2 ->
                it2.expectForm<SymbolForm, String> { symForm -> symForm.sym }
            }

            it.nested(VectorForm::forms) {
                it.zeroOrMore {
                    TODO()
                }
            }

            TODO()
        }

        private val callAnalyser: FormsAnalyser<ValueExpr> = {
            val call = exprAnalyser(it)
            CallExpr(call, it.zeroOrMore(exprAnalyser))
        }

        private val listAnalyser: FormsAnalyser<ValueExpr> = {
            if (it.forms.isEmpty()) throw ExpectedForm

            val firstForm = it.forms[0]

            if (firstForm is Form.SymbolForm) {
                if (firstForm.ns == null) {
                    it.forms = it.forms.drop(1)
                    when (firstForm.sym) {
                        "if" -> {
                            ifAnalyser(it)
                        }

                        "fn" -> {
                            fnAnalyser(it)
                        }

                        else -> callAnalyser(it)
                    }
                } else {
                    TODO("global call")
                }
            } else {
                callAnalyser(it)
            }
        }

        private fun collAnalyser(transform: (List<ValueExpr>) -> ValueExpr): FormsAnalyser<ValueExpr> = {
            transform(it.zeroOrMore(exprAnalyser))
        }

        private val exprAnalyser: FormsAnalyser<ValueExpr> = {
            it.expectForm<Form, ValueExpr> { form ->
                when (form) {
                    is Form.BooleanForm -> BooleanExpr(form.bool)
                    is Form.StringForm -> StringExpr(form.string)
                    is Form.IntForm -> IntExpr(form.int)
                    is Form.BigIntForm -> BigIntExpr(form.bigInt)
                    is Form.FloatForm -> FloatExpr(form.float)
                    is Form.BigFloatForm -> BigFloatExpr(form.bigFloat)
                    is Form.SymbolForm -> TODO()
                    is Form.KeywordForm -> TODO()
                    is Form.ListForm -> listAnalyser(AnalyserState(form.forms))
                    is Form.VectorForm -> collAnalyser(::VectorExpr)(AnalyserState(form.forms))
                    is Form.SetForm -> collAnalyser(::SetExpr)(AnalyserState(form.forms))
                    is Form.RecordForm -> TODO()
                    is Form.QuoteForm -> TODO()
                    is Form.UnquoteForm -> TODO()
                    is Form.UnquoteSplicingForm -> TODO()
                }
            }
        }

        fun analyse(form: Form): ValueExpr = exprAnalyser(AnalyserState(listOf(form)))
    }

    fun analyseValueExpr(form: Form): ValueExpr = Analyser.AnalyserCtx().analyse(form)

}

