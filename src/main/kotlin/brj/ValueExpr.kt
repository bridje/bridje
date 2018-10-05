package brj

import java.math.BigDecimal
import java.math.BigInteger

sealed class ValueExpr {
    data class BooleanExpr(val boolean: Boolean) : ValueExpr()
    data class StringExpr(val string: String) : ValueExpr()
    data class IntExpr(val int: Long) : ValueExpr()
    data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
    data class FloatExpr(val float: Double) : ValueExpr()
    data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()

    data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()
    data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()

    data class CallExpr(val f: ValueExpr, val args: List<ValueExpr>) : ValueExpr()

    data class FnExpr(val fnName: Symbol?, val params: List<LocalVar>, val expr: ValueExpr) : ValueExpr()
    data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()
    data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

    data class Binding(val localVar: LocalVar, val expr: ValueExpr)
    data class LetExpr(val bindings: List<Binding>, val expr: ValueExpr) : ValueExpr()

    data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()

    @Suppress("NestedLambdaShadowedImplicitParameter")
    data class ValueExprAnalyser(val env: BrjEnv, val ns: Symbol, val locals: Map<Symbol, LocalVar> = emptyMap(), val loopLocals: List<LocalVar>? = null) {
        private val ifAnalyser: FormsAnalyser<ValueExpr> = {
            val predExpr = exprAnalyser(it)
            val thenExpr = exprAnalyser(it)
            val elseExpr = exprAnalyser(it)
            it.expectEnd()
            IfExpr(predExpr, thenExpr, elseExpr)
        }

        private val letAnalyser: FormsAnalyser<ValueExpr> = {
            var ana = this
            LetExpr(
                it.nested(Form.VectorForm::forms) { bindingState ->
                    bindingState.varargs {
                        val bindingCtx = ana.copy(loopLocals = null)

                        val sym = it.expectForm<Form.SymbolForm>().sym
                        val localVar = LocalVar(sym)

                        val expr = bindingCtx.exprAnalyser(it)

                        ana = ana.copy(locals = ana.locals.plus(sym to localVar))
                        Binding(localVar, expr)
                    }
                },

                ana.exprAnalyser(it))
        }

        private val fnAnalyser: FormsAnalyser<ValueExpr> = {
            val fnName: Symbol? = it.maybe { it.expectForm<Form.SymbolForm>().sym }

            val paramNames = it.nested(Form.VectorForm::forms) {
                it.varargs {
                    it.expectForm<Form.SymbolForm>().sym
                }
            }

            val newLocals = paramNames.map { it to LocalVar(it) }

            val ana = this.copy(locals = locals.plus(newLocals))

            val bodyExpr = ana.doAnalyser(it)

            FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr)
        }

        private val callAnalyser: FormsAnalyser<ValueExpr> = {
            val call = exprAnalyser(it)
            CallExpr(call, it.varargs(exprAnalyser))
        }

        private val doAnalyser: FormsAnalyser<ValueExpr> = {
            val exprs = listOf(exprAnalyser(it)).plus(it.varargs(exprAnalyser))
            DoExpr(exprs.dropLast(1), exprs.last())
        }

        private val listAnalyser: FormsAnalyser<ValueExpr> = {
            if (it.forms.isEmpty()) throw Analyser.AnalyserError.ExpectedForm

            val firstForm = it.forms[0]

            if (firstForm is Form.SymbolForm) {
                it.forms = it.forms.drop(1)
                when (firstForm.sym) {
                    Analyser.IF -> ifAnalyser(it)
                    Analyser.FN -> fnAnalyser(it)
                    Analyser.LET -> letAnalyser(it)
                    Analyser.DO -> doAnalyser(it)
                    else -> callAnalyser(it)
                }
            } else {
                callAnalyser(it)
            }
        }

        private fun collAnalyser(transform: (List<ValueExpr>) -> ValueExpr): FormsAnalyser<ValueExpr> = {
            transform(it.varargs(exprAnalyser))
        }

        private fun analyseSymbol(sym: Symbol): ValueExpr {
            return LocalVarExpr(locals[sym] ?: throw Analyser.AnalyserError.ResolutionError(sym))
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
                is Form.ListForm -> listAnalyser(Analyser.AnalyserState(form.forms))
                is Form.VectorForm -> collAnalyser(ValueExpr::VectorExpr)(Analyser.AnalyserState(form.forms))
                is Form.SetForm -> collAnalyser(ValueExpr::SetExpr)(Analyser.AnalyserState(form.forms))
                is Form.RecordForm -> TODO()
                is Form.QuoteForm -> TODO()
                is Form.UnquoteForm -> TODO()
                is Form.UnquoteSplicingForm -> TODO()
            }
        }

        fun analyseValueExpr(forms: List<Form>): ValueExpr = doAnalyser(Analyser.AnalyserState(forms))
    }
}