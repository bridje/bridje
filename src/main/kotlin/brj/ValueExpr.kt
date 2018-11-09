package brj

import brj.Analyser.AnalyserError.TodoError
import brj.BrjEnv.NSEnv
import brj.BrjEnv.NSEnv.GlobalVar
import brj.Form.*
import brj.ValueExpr.CaseExpr.CaseClause
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

    data class FnExpr(val fnName: ASymbol.Symbol? = null, val params: List<LocalVar>, val expr: ValueExpr) : ValueExpr()
    data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()
    data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

    data class LetExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr() {
        data class LetBinding(val localVar: LocalVar, val expr: ValueExpr)
    }

    data class CaseExpr(val expr: ValueExpr, val clauses: List<CaseClause>, val defaultExpr: ValueExpr?) : ValueExpr() {
        data class CaseClause(val kw: AQSymbol.QKeyword, val bindings: List<LocalVar>?, val bodyExpr: ValueExpr)
    }

    data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()
    data class GlobalVarExpr(val globalVar: GlobalVar) : ValueExpr()

    data class ConstructorExpr(val constructor: NSEnv.DataTypeConstructor) : ValueExpr()

    @Suppress("NestedLambdaShadowedImplicitParameter")
    data class ValueExprAnalyser(val env: BrjEnv, val nsEnv: NSEnv, val locals: Map<ASymbol.Symbol, LocalVar> = emptyMap(), val loopLocals: List<LocalVar>? = null) {
        companion object {
            val IF = ASymbol.Symbol.intern("if")
            val FN = ASymbol.Symbol.intern("fn")
            val LET = ASymbol.Symbol.intern("let")
            val DO = ASymbol.Symbol.intern("do")
            val CASE = ASymbol.Symbol.intern("case")
        }

        private fun ifAnalyser(it: Analyser.AnalyserState): ValueExpr {
            val predExpr = exprAnalyser(it)
            val thenExpr = exprAnalyser(it)
            val elseExpr = exprAnalyser(it)
            it.expectEnd()
            return IfExpr(predExpr, thenExpr, elseExpr)
        }

        private fun letAnalyser(it: Analyser.AnalyserState): ValueExpr {
            var ana = this
            return LetExpr(
                it.nested(Form.VectorForm::forms) { bindingState ->
                    bindingState.varargs {
                        val bindingCtx = ana.copy(loopLocals = null)

                        val sym = it.expectForm<ASymbolForm.SymbolForm>().sym
                        val localVar = LocalVar(sym)

                        val expr = bindingCtx.exprAnalyser(it)

                        ana = ana.copy(locals = ana.locals.plus(sym to localVar))
                        LetExpr.LetBinding(localVar, expr)
                    }
                },

                ana.exprAnalyser(it))
        }

        private fun fnAnalyser(it: Analyser.AnalyserState): ValueExpr {
            val fnName: ASymbol.Symbol? = it.maybe { it.expectForm<ASymbolForm.SymbolForm>().sym }

            val paramNames = it.nested(Form.VectorForm::forms) {
                it.varargs {
                    it.expectForm<ASymbolForm.SymbolForm>().sym
                }
            }

            val newLocals = paramNames.map { it to LocalVar(it) }

            val ana = this.copy(locals = locals.plus(newLocals))

            val bodyExpr = ana.doAnalyser(it)

            return FnExpr(fnName, newLocals.map(Pair<ASymbol.Symbol, LocalVar>::second), bodyExpr)
        }

        private fun callAnalyser(it: Analyser.AnalyserState): ValueExpr =
            CallExpr(exprAnalyser(it), it.varargs(::exprAnalyser))

        private fun doAnalyser(it: Analyser.AnalyserState): ValueExpr {
            val exprs = listOf(exprAnalyser(it)).plus(it.varargs(::exprAnalyser))
            return DoExpr(exprs.dropLast(1), exprs.last())
        }

        private fun caseAnalyser(it: Analyser.AnalyserState): ValueExpr {
            val expr = exprAnalyser(it)

            val clauses = mutableListOf<CaseClause>()

            while (it.forms.size > 1) {

//                clauses.add(CaseClause(it.expectForm<QKeywordForm>().kw, ))
                TODO()
            }

            val defaultExpr = if (it.forms.isNotEmpty()) exprAnalyser(it) else null

            it.expectEnd()

            return CaseExpr(expr, clauses.toList(), defaultExpr)
        }

        private fun listAnalyser(it: Analyser.AnalyserState): ValueExpr {
            if (it.forms.isEmpty()) throw Analyser.AnalyserError.ExpectedForm

            val firstForm = it.forms[0]

            return if (firstForm is ASymbolForm.SymbolForm) {
                it.forms = it.forms.drop(1)
                when (firstForm.sym) {
                    IF -> ifAnalyser(it)
                    FN -> fnAnalyser(it)
                    LET -> letAnalyser(it)
                    DO -> doAnalyser(it)
                    CASE -> caseAnalyser(it)
                    else -> callAnalyser(it)
                }
            } else {
                callAnalyser(it)
            }
        }

        private fun collAnalyser(transform: (List<ValueExpr>) -> ValueExpr): FormsAnalyser<ValueExpr> = {
            transform(it.varargs(::exprAnalyser))
        }

        private fun analyseSymbol(sym: ASymbol): ValueExpr {
            return (locals[sym]?.let { LocalVarExpr(it) })

                ?: nsEnv.vars[sym]
                    ?.let(::GlobalVarExpr)

                ?: (nsEnv.refers[sym]
                    ?.let { refer -> env.nses[refer.ns]?.vars?.get(refer.name) }
                    ?.let(::GlobalVarExpr))

                ?: throw Analyser.AnalyserError.ResolutionError(sym)
        }

        private fun analyseSymbol(sym: AQSymbol): ValueExpr {
            val globalVar = env.nses[sym.ns]?.vars?.get(sym.name) ?: throw TodoError
            return GlobalVarExpr(globalVar)
        }

        private fun exprAnalyser(it: Analyser.AnalyserState): ValueExpr {
            val form = it.expectForm<Form>()

            return when (form) {
                is BooleanForm -> BooleanExpr(form.bool)
                is StringForm -> StringExpr(form.string)
                is IntForm -> IntExpr(form.int)
                is BigIntForm -> BigIntExpr(form.bigInt)
                is FloatForm -> FloatExpr(form.float)
                is BigFloatForm -> BigFloatExpr(form.bigFloat)
                is ASymbolForm -> analyseSymbol(form.sym)
                is AQSymbolForm -> analyseSymbol(form.sym)
                is ListForm -> listAnalyser(Analyser.AnalyserState(form.forms))
                is VectorForm -> collAnalyser(ValueExpr::VectorExpr)(Analyser.AnalyserState(form.forms))
                is SetForm -> collAnalyser(ValueExpr::SetExpr)(Analyser.AnalyserState(form.forms))
                is RecordForm -> TODO()
                is QuoteForm -> TODO()
                is UnquoteForm -> TODO()
                is UnquoteSplicingForm -> TODO()
            }
        }

        fun analyseValueExpr(forms: List<Form>): ValueExpr = doAnalyser(Analyser.AnalyserState(forms))
    }
}