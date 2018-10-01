package brj

import brj.Analyser.AnalyserCtx.AnalyserState.Companion.analyser
import brj.Analyser.FormsAnalyser.AnalyserError.ExpectedForm
import brj.Analyser.FormsAnalyser.AnalyserError.UnexpectedForms
import brj.Analyser.FormsAnalyser.Result
import brj.Analyser.FormsAnalyser.Result.Failure
import brj.Analyser.FormsAnalyser.Result.Success
import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*

object Analyser {

    interface FormsAnalyser<R> {
        sealed class AnalyserError : Exception() {
            object ExpectedForm : AnalyserError()
            data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
        }

        sealed class Result<R> {
            abstract fun orThrow(): R

            data class Failure<R>(val error: AnalyserError) : Result<R>() {
                override fun orThrow(): R = throw error
            }

            data class Success<R>(val res: R, val remaining: List<Form>) : Result<R>() {
                override fun orThrow(): R = res
            }
        }

        fun analyse(forms: List<Form>): Result<R>

    }

    data class AnalyserCtx(val loopLocals: List<LocalVar>? = null) {
        data class AnalyserState(var forms: List<Form>) {
            companion object {
                fun <R> analyser(f: (AnalyserState) -> R): FormsAnalyser<R> = object : FormsAnalyser<R> {
                    override fun analyse(forms: List<Form>): Result<R> =
                        try {
                            val state = AnalyserState(forms)
                            val res = f(state)
                            Success(res, state.forms)
                        } catch (e: FormsAnalyser.AnalyserError) {
                            Failure(e)
                        }
                }
            }

            fun <R> zeroOrMore(a: FormsAnalyser<R>): List<R> {
                val ret: MutableList<R> = mutableListOf()

                while (true) {
                    val res = a.analyse(forms)

                    when (res) {
                        is Success<R> -> {
                            forms = res.remaining
                            ret.add(res.res)
                        }

                        is Failure<R> -> {
                            return ret.toList()
                        }
                    }
                }
            }

            fun expectEnd() {
                if (forms.isNotEmpty()) {
                    throw UnexpectedForms(forms)
                }
            }

            fun <R> expectForm(f: (Form) -> R): R =
                if (forms.isNotEmpty()) {
                    val r = f(forms.first())
                    forms = forms.drop(1)
                    r
                } else {
                    throw ExpectedForm
                }

            fun <R> analyse(a: FormsAnalyser<R>): R =
                a.analyse(forms).let {
                    when (it) {
                        is Failure<R> -> throw it.error

                        is Success<R> -> {
                            forms = it.remaining; return it.res
                        }
                    }
                }
        }

        private val ifAnalyser: FormsAnalyser<ValueExpr> = analyser {
            val predExpr = it.analyse(exprAnalyser)
            val thenExpr = it.analyse(exprAnalyser)
            val elseExpr = it.analyse(exprAnalyser)
            it.expectEnd()
            IfExpr(predExpr, thenExpr, elseExpr)
        }

        private val callAnalyser: FormsAnalyser<ValueExpr> = analyser {
            val call = it.analyse(exprAnalyser)
            CallExpr(call, it.zeroOrMore(exprAnalyser))
        }

        private val listAnalyser = analyser {
            if (it.forms.isEmpty()) throw ExpectedForm

            val firstForm = it.forms[0]

            if (firstForm is Form.SymbolForm) {
                if (firstForm.ns == null) {
                    it.forms = it.forms.drop(1)
                    when (firstForm.sym) {
                        "if" -> {
                            return@analyser it.analyse(ifAnalyser)
                        }
                    }
                } else {
                    TODO("global call")
                }
            }

            it.analyse(callAnalyser)
        }

        private fun collParser(transform: (List<ValueExpr>) -> ValueExpr): FormsAnalyser<ValueExpr> = analyser {
            transform(it.zeroOrMore(exprAnalyser))
        }

        private val exprAnalyser: FormsAnalyser<ValueExpr> = analyser {
            return@analyser it.expectForm { form ->
                when (form) {
                    is Form.BooleanForm -> BooleanExpr(form.bool)
                    is Form.StringForm -> StringExpr(form.string)
                    is Form.IntForm -> IntExpr(form.int)
                    is Form.BigIntForm -> BigIntExpr(form.bigInt)
                    is Form.FloatForm -> FloatExpr(form.float)
                    is Form.BigFloatForm -> BigFloatExpr(form.bigFloat)
                    is Form.SymbolForm -> TODO()
                    is Form.KeywordForm -> TODO()
                    is Form.ListForm -> listAnalyser.analyse(form.forms).orThrow()
                    is Form.VectorForm -> collParser(::VectorExpr).analyse(form.forms).orThrow()
                    is Form.SetForm -> collParser(::SetExpr).analyse(form.forms).orThrow()
                    is Form.RecordForm -> TODO()
                    is Form.QuoteForm -> TODO()
                    is Form.UnquoteForm -> TODO()
                    is Form.UnquoteSplicingForm -> TODO()
                }
            }
        }

        fun analyse(form: Form): ValueExpr {
            val result = exprAnalyser.analyse(listOf(form))

            return when (result) {
                is Success -> result.res
                is Failure -> throw result.error
            }
        }
    }

    fun analyseValueExpr(form: Form): ValueExpr = Analyser.AnalyserCtx().analyse(form)

}

