package brj

import brj.Analyser.AnalyserError.EmptyList
import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*

object Analyser {
    data class AnalyserException(val errors: List<AnalyserError>) : Exception()

    sealed class AnalyserError(open val form: Form) {
        data class EmptyList(override val form: Form) : AnalyserError(form)
    }

    data class AnalyserCtx(val errors: MutableList<AnalyserError> = mutableListOf(), val loopLocals: List<LocalVar>? = null) {
        private fun analyserError(error: AnalyserError): ValueExpr? {
            errors.add(error)
            return null
        }

        private fun analyseIfForm(forms: List<Form>): ValueExpr? {
            TODO("not implemented")
        }

        private fun analyseListValueForm(form: Form.ListForm): ValueExpr? {
            val forms = form.forms

            if (forms.isEmpty()) return analyserError(EmptyList(form))

            val firstForm = forms[0]

            if (firstForm is Form.SymbolForm)
                if (firstForm.ns == null) {
                    when (firstForm.sym) {
                        "if" -> return analyseIfForm(forms)
                    }
                } else {
                    TODO("global call")
                }

            val ctx = copy(loopLocals = null)
            return ctx.analyseValueForm(firstForm)?.let { CallExpr(it, forms.asSequence().map(ctx::analyseValueForm).filterNotNull().toList()) }
        }

        fun analyseValueForm(form: Form): ValueExpr? =
            when (form) {
                is Form.BooleanForm -> BooleanExpr(form.bool)
                is Form.StringForm -> StringExpr(form.string)
                is Form.IntForm -> IntExpr(form.int)
                is Form.BigIntForm -> BigIntExpr(form.bigInt)
                is Form.FloatForm -> FloatExpr(form.float)
                is Form.BigFloatForm -> BigFloatExpr(form.bigFloat)

                is Form.VectorForm -> VectorExpr(form.forms.asSequence().map(copy(loopLocals = null)::analyseValueForm).filterNotNull().toList())
                is Form.SetForm -> SetExpr(form.forms.asSequence().map(copy(loopLocals = null)::analyseValueForm).filterNotNull().toList())
                is Form.RecordForm -> TODO()

                is Form.SymbolForm -> TODO()
                is Form.KeywordForm -> TODO()

                is Form.QuoteForm -> TODO()
                is Form.UnquoteForm -> TODO()
                is Form.UnquoteSplicingForm -> TODO()

                is Form.ListForm -> analyseListValueForm(form)
            }
    }

    fun analyseValueForm(form: Form): ValueExpr {
        val ctx = AnalyserCtx()
        val expr = ctx.analyseValueForm(form)

        return if (ctx.errors.isEmpty()) expr!! else throw AnalyserException(ctx.errors)
    }
}
