package brj

import brj.Analyser.AnalyserError.*
import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import brj.Form.SymbolForm
import brj.Form.VectorForm

typealias FormsAnalyser<R> = (Analyser.AnalyserCtx.AnalyserState) -> R

object Analyser {

    sealed class AnalyserError : Exception() {
        object ExpectedForm : AnalyserError()
        data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
        data class ResolutionError(val sym: Symbol) : AnalyserError()
    }

    data class AnalyserCtx(val locals: Map<Symbol, LocalVar> = emptyMap(), val loopLocals: List<LocalVar>? = null) {

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
                a(AnalyserState(f(expectForm())))
        }

        private val ifAnalyser: FormsAnalyser<ValueExpr> = {
            val predExpr = exprAnalyser(it)
            val thenExpr = exprAnalyser(it)
            val elseExpr = exprAnalyser(it)
            it.expectEnd()
            IfExpr(predExpr, thenExpr, elseExpr)
        }

        @Suppress("NestedLambdaShadowedImplicitParameter")
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

        @Suppress("NestedLambdaShadowedImplicitParameter")
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

            val ctx = AnalyserCtx(locals = locals.plus(newLocals))

            val bodyExpr = ctx.exprAnalyser(it)
            it.expectEnd()

            FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr)
        }

        private val callAnalyser: FormsAnalyser<ValueExpr> = {
            val call = exprAnalyser(it)
            CallExpr(call, it.zeroOrMore(exprAnalyser))
        }

        private val listAnalyser: FormsAnalyser<ValueExpr> = {
            if (it.forms.isEmpty()) throw ExpectedForm

            val firstForm = it.forms[0]

            if (firstForm is Form.SymbolForm) {
                it.forms = it.forms.drop(1)
                when (firstForm.sym) {
                    Symbol("if") -> {
                        ifAnalyser(it)
                    }

                    Symbol("fn") -> {
                        fnAnalyser(it)
                    }

                    Symbol("let") -> {
                        letAnalyser(it)
                    }

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
                is Form.BooleanForm -> BooleanExpr(form.bool)
                is Form.StringForm -> StringExpr(form.string)
                is Form.IntForm -> IntExpr(form.int)
                is Form.BigIntForm -> BigIntExpr(form.bigInt)
                is Form.FloatForm -> FloatExpr(form.float)
                is Form.BigFloatForm -> BigFloatExpr(form.bigFloat)
                is Form.SymbolForm -> analyseSymbol(form.sym)
                is Form.NamespacedSymbolForm -> TODO()
                is Form.KeywordForm -> TODO()
                is Form.NamespacedKeywordForm -> TODO()
                is Form.ListForm -> listAnalyser(AnalyserState(form.forms))
                is Form.VectorForm -> collAnalyser(::VectorExpr)(AnalyserState(form.forms))
                is Form.SetForm -> collAnalyser(::SetExpr)(AnalyserState(form.forms))
                is Form.RecordForm -> TODO()
                is Form.QuoteForm -> TODO()
                is Form.UnquoteForm -> TODO()
                is Form.UnquoteSplicingForm -> TODO()
            }
        }

        fun analyse(form: Form): ValueExpr = exprAnalyser(AnalyserState(listOf(form)))
    }

    fun analyseValueExpr(form: Form): ValueExpr = Analyser.AnalyserCtx().analyse(form)

}

