package brj

import brj.Analyser.ParseResult.AnalyserError.EmptyList
import brj.Analyser.ParseResult.ErrorResult
import brj.Analyser.ParseResult.Parsed
import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*

object Analyser {
    interface Parser<out R> {
        fun parse(forms: List<Form>): ParseResult<R>
    }

    sealed class ParseResult<out R> {
        sealed class AnalyserError : Exception() {
            data class EmptyList(val form: Form) : AnalyserError()
            data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
            object ExpectedForm : AnalyserError()

        }

        data class ErrorResult<R>(val error: AnalyserError) : ParseResult<R>()
        data class Parsed<R>(val res: R, val remaining: List<Form>) : ParseResult<R>()
    }

    data class ParserState<out R>(var forms: List<Form>) {
        fun <R> optional(p: Parser<R>): R? =
            p.parse(forms).let {
                when (it) {
                    is Parsed -> {
                        forms = it.remaining
                        it.res
                    }

                    is ErrorResult -> null
                }
            }

        fun <R> zeroOrMore(p: Parser<R>): List<R> {
            val res = mutableListOf<R>()

            while (true) {
                p.parse(forms).let {
                    when (it) {
                        is ErrorResult -> return res
                        is Parsed -> {
                            res += it.res; forms = it.remaining
                        }
                    }
                }
            }
        }

        companion object {
            fun <R> parser(): ParserState<R> {

            }
        }
    }

    data class AnalyserCtx(val errors: MutableList<ErrorResult.AnalyserError> = mutableListOf(), val loopLocals: List<LocalVar>? = null) {
        private val ifParser: Parser<ValueExpr> = parser().run {
            val predExpr = parseExpr()
        }

        private val listParser = object : Parser<ValueExpr> {
            override fun parse(forms: List<Form>): Parser.ParseResult<ValueExpr> {
                if (forms.isEmpty()) return ErrorResult(EmptyList())

                val firstForm = forms[0]

                if (firstForm is Form.SymbolForm)
                    if (firstForm.ns == null) {
                        when (firstForm.sym) {
                            "if" -> return ifParser.parse(forms.drop(1))
                        }
                    } else {
                        TODO("global call")
                    }

                val ctx = copy(loopLocals = null)

                return ctx.analyseValueForm(firstForm)?.let { CallExpr(it, forms.asSequence().map(ctx::analyseValueForm).filterNotNull().toList()) }
            }
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
