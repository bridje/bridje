package brj

import brj.Analyser.AnalyserCtx.AnalysisResult.EvalExpr
import brj.Analyser.AnalyserError.*
import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import brj.Form.*

typealias FormsAnalyser<R> = (Analyser.AnalyserState) -> R

@Suppress("NestedLambdaShadowedImplicitParameter")
object Analyser {

    sealed class AnalyserError : Exception() {
        object ExpectedForm : AnalyserError()
        data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
        data class ResolutionError(val sym: Symbol) : AnalyserError()
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

        inline fun <reified F : Form, R> nested(f: (F) -> List<Form>, a: FormsAnalyser<R>): R =
            a(AnalyserState(f(expectForm())))
    }

    data class Var(val type: Nothing)
    data class NSEnv(val vars: Map<Symbol, Var>)
    data class Env(val nses: Map<Symbol, NSEnv> = emptyMap())

    data class AnalyserCtx(val env: Env, val ns: Symbol, val locals: Map<Symbol, LocalVar> = emptyMap(), val loopLocals: List<LocalVar>? = null) {
        private val ifAnalyser: FormsAnalyser<ValueExpr> = {
            val predExpr = exprAnalyser(it)
            val thenExpr = exprAnalyser(it)
            val elseExpr = exprAnalyser(it)
            it.expectEnd()
            IfExpr(predExpr, thenExpr, elseExpr)
        }

        private val letAnalyser: FormsAnalyser<ValueExpr> = {
            var ctx = this
            LetExpr(
                it.nested(VectorForm::forms) { bindingState ->
                    bindingState.zeroOrMore {
                        val bindingCtx = ctx.copy(loopLocals = null)

                        val sym = it.expectForm<SymbolForm>().sym
                        val localVar = LocalVar(sym)

                        val expr = bindingCtx.exprAnalyser(it)

                        ctx = ctx.copy(locals = ctx.locals.plus(Pair(sym, localVar)))
                        Binding(localVar, expr)
                    }.also { bindingState.expectEnd() }
                },

                ctx.exprAnalyser(it))
        }

        private val fnAnalyser: FormsAnalyser<ValueExpr> = {
            val fnName: Symbol? = it.maybe { it.expectForm<SymbolForm>().sym }

            val paramNames = it.nested(VectorForm::forms) {
                val paramNames = it.zeroOrMore {
                    it.expectForm<SymbolForm>().sym
                }
                it.expectEnd()
                paramNames
            }

            val newLocals = paramNames.map { Pair(it, LocalVar(it)) }

            val ctx = this.copy(locals = locals.plus(newLocals))

            val bodyExpr = ctx.exprAnalyser(it)
            it.expectEnd()

            FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr)
        }

        private val callAnalyser: FormsAnalyser<ValueExpr> = {
            val call = exprAnalyser(it)
            CallExpr(call, it.zeroOrMore(exprAnalyser))
        }

        private val doAnalyser: FormsAnalyser<ValueExpr> = {
            val exprs = listOf(exprAnalyser(it)).plus(it.zeroOrMore(exprAnalyser))
            it.expectEnd()
            DoExpr(exprs.dropLast(1), exprs.last())
        }

        private val listAnalyser: FormsAnalyser<ValueExpr> = {
            if (it.forms.isEmpty()) throw ExpectedForm

            val firstForm = it.forms[0]

            if (firstForm is Form.SymbolForm) {
                it.forms = it.forms.drop(1)
                when (firstForm.sym) {
                    Symbol("if") -> ifAnalyser(it)
                    Symbol("fn") -> fnAnalyser(it)
                    Symbol("let") -> letAnalyser(it)
                    Symbol("do") -> doAnalyser(it)
                    else -> callAnalyser(it)
                }
            } else {
                callAnalyser(it)
            }
        }

        private fun collAnalyser(transform: (List<ValueExpr>) -> ValueExpr): FormsAnalyser<ValueExpr> = {
            transform(it.zeroOrMore(exprAnalyser)).also { _ -> it.expectEnd() }
        }

        private fun analyseSymbol(sym: Symbol): ValueExpr {
            return LocalVarExpr(locals.get(sym) ?: throw ResolutionError(sym))
        }

        private val exprAnalyser: FormsAnalyser<ValueExpr> = {
            val form = it.expectForm<Form>()

            when (form) {
                is BooleanForm -> BooleanExpr(form.bool)
                is StringForm -> StringExpr(form.string)
                is IntForm -> IntExpr(form.int)
                is BigIntForm -> BigIntExpr(form.bigInt)
                is FloatForm -> FloatExpr(form.float)
                is BigFloatForm -> BigFloatExpr(form.bigFloat)
                is SymbolForm -> analyseSymbol(form.sym)
                is NamespacedSymbolForm -> TODO()
                is KeywordForm -> TODO()
                is NamespacedKeywordForm -> TODO()
                is ListForm -> listAnalyser(AnalyserState(form.forms))
                is VectorForm -> collAnalyser(::VectorExpr)(AnalyserState(form.forms))
                is SetForm -> collAnalyser(::SetExpr)(AnalyserState(form.forms))
                is RecordForm -> TODO()
                is QuoteForm -> TODO()
                is UnquoteForm -> TODO()
                is UnquoteSplicingForm -> TODO()
            }
        }

        sealed class AnalysisResult {
            data class UpdateEnv(val ns: Symbol) : AnalysisResult()
            data class EvalExpr(val expr: ValueExpr) : AnalysisResult()
        }

        companion object {
            private val inNsAnalyser: FormsAnalyser<Symbol?> = {
                it.maybe {
                    it.nested(ListForm::forms) {
                        val inNsSym = it.expectForm<SymbolForm>().sym
                        val nsSym = it.expectForm<SymbolForm>().sym
                        if (inNsSym == Symbol("in-ns")) nsSym else null
                    }
                }
            }

            fun analyseForms(env: Env, forms: List<Form>): AnalysisResult {
                val state = AnalyserState(forms)

                val ns = inNsAnalyser(state) ?: Symbol("user")
                val ctx = AnalyserCtx(env, ns)

                return EvalExpr(ctx.doAnalyser(state))
            }
        }
    }
}

