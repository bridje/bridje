package brj

import java.math.BigDecimal
import java.math.BigInteger

sealed class ValueExpr

data class BooleanExpr(val boolean: Boolean) : ValueExpr()
data class StringExpr(val string: String) : ValueExpr()
data class IntExpr(val int: Long) : ValueExpr()
data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
data class FloatExpr(val float: Double) : ValueExpr()
data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()

data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()
data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()

data class CallExpr(val f: ValueExpr, val args: List<ValueExpr>) : ValueExpr()

data class FnExpr(val fnName: Symbol? = null, val params: List<LocalVar>, val expr: ValueExpr) : ValueExpr()
data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()
data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

data class LetBinding(val localVar: LocalVar, val expr: ValueExpr)
data class LetExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()

data class CaseClause(val constructor: DataTypeConstructor, val bindings: List<LocalVar>?, val bodyExpr: ValueExpr)
data class CaseExpr(val expr: ValueExpr, val clauses: List<CaseClause>, val defaultExpr: ValueExpr?) : ValueExpr()

data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()
data class GlobalVarExpr(val globalVar: GlobalVar) : ValueExpr()

@Suppress("NestedLambdaShadowedImplicitParameter")
internal data class ValueExprAnalyser(val env: Env, val nsEnv: NSEnv, val locals: Map<Symbol, LocalVar> = emptyMap(), val loopLocals: List<LocalVar>? = null) {
    companion object {
        val IF = Symbol.intern("if")
        val FN = Symbol.intern("fn")
        val LET = Symbol.intern("let")
        val DO = Symbol.intern("do")
        val CASE = Symbol.intern("case")
    }

    private fun resolve(sym: Symbol) = resolve(env, nsEnv, sym)
    private fun resolve(sym: QSymbol) = resolve(env, nsEnv, sym)

    private fun ifAnalyser(it: AnalyserState): ValueExpr {
        val predExpr = exprAnalyser(it)
        val thenExpr = exprAnalyser(it)
        val elseExpr = exprAnalyser(it)
        it.expectEnd()
        return IfExpr(predExpr, thenExpr, elseExpr)
    }

    private fun letAnalyser(it: AnalyserState): ValueExpr {
        var ana = this
        return LetExpr(
                it.nested(VectorForm::forms) { bindingState ->
                    bindingState.varargs {
                        val bindingCtx = ana.copy(loopLocals = null)

                        val sym = it.expectForm<SymbolForm>().sym
                        val localVar = LocalVar(sym)

                        val expr = bindingCtx.exprAnalyser(it)

                        ana = ana.copy(locals = ana.locals.plus(sym to localVar))
                        LetBinding(localVar, expr)
                    }
                },

                ana.exprAnalyser(it))
    }

    private fun fnAnalyser(it: AnalyserState): ValueExpr {
        val fnName: Symbol? = it.maybe { it.expectForm<SymbolForm>().sym }

        val paramNames = it.nested(VectorForm::forms) {
            it.varargs {
                it.expectForm<SymbolForm>().sym
            }
        }

        val newLocals = paramNames.map { it to LocalVar(it) }

        val ana = this.copy(locals = locals.plus(newLocals))

        val bodyExpr = ana.doAnalyser(it)

        return FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr)
    }

    private fun callAnalyser(it: AnalyserState): ValueExpr =
            CallExpr(exprAnalyser(it), it.varargs(::exprAnalyser))

    private fun doAnalyser(it: AnalyserState): ValueExpr {
        val exprs = listOf(exprAnalyser(it)).plus(it.varargs(::exprAnalyser))
        return DoExpr(exprs.dropLast(1), exprs.last())
    }

    private fun caseAnalyser(it: AnalyserState): ValueExpr {
        val expr = exprAnalyser(it)

        val clauses = mutableListOf<CaseClause>()

        while (it.forms.size > 1) {
            val clauseForm = it.expectForm<Form>()

            fun resolveConstructor(form: Form): DataTypeConstructor {
                return when (form) {
                    is SymbolForm -> resolve(form.sym)
                    is QSymbolForm -> resolve(form.sym)
                    else -> TODO()
                } as? DataTypeConstructor ?: TODO()
            }

            val (constructor, paramSyms) = when (clauseForm) {
                is SymbolForm, is QSymbolForm -> Pair(resolveConstructor(clauseForm), null)
                is ListForm -> {
                    it.nested(clauseForm.forms) {
                        Pair(resolveConstructor(it.expectForm()), it.varargs { it.expectForm<SymbolForm>().sym })

                    }
                }
                else -> TODO()
            }

            val localVars = paramSyms?.map { it to LocalVar(it) }

            clauses += CaseClause(
                    constructor,
                    localVars?.map { it.second },
                    (if (localVars == null) this else copy(locals = locals + localVars))
                            .exprAnalyser(it))
        }

        val defaultExpr = if (it.forms.isNotEmpty()) exprAnalyser(it) else null

        it.expectEnd()

        return CaseExpr(expr, clauses.toList(), defaultExpr)
    }

    private fun listAnalyser(it: AnalyserState): ValueExpr {
        if (it.forms.isEmpty()) throw AnalyserError.ExpectedForm

        val firstForm = it.forms[0]

        return if (firstForm is SymbolForm) {
            when (firstForm.sym) {
                IF, FN, LET, DO, CASE -> it.forms = it.forms.drop(1)
            }

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

    private fun exprAnalyser(it: AnalyserState): ValueExpr {
        val form = it.expectForm<Form>()

        return when (form) {
            is BooleanForm -> BooleanExpr(form.bool)
            is StringForm -> StringExpr(form.string)
            is IntForm -> IntExpr(form.int)
            is BigIntForm -> BigIntExpr(form.bigInt)
            is FloatForm -> FloatExpr(form.float)
            is BigFloatForm -> BigFloatExpr(form.bigFloat)

            is SymbolForm ->
                (locals[form.sym]?.let { LocalVarExpr(it) })
                        ?: resolve(form.sym)?.let(::GlobalVarExpr)
                        ?: throw AnalyserError.ResolutionError(form.sym)

            is QSymbolForm ->
                GlobalVarExpr(resolve(form.sym)
                        ?: throw AnalyserError.ResolutionError(form.sym))

            is ListForm -> listAnalyser(AnalyserState(form.forms))
            is VectorForm -> collAnalyser(::VectorExpr)(AnalyserState(form.forms))
            is SetForm -> collAnalyser(::SetExpr)(AnalyserState(form.forms))
            is RecordForm -> TODO()
            is QuoteForm -> TODO()
            is UnquoteForm -> TODO()
            is UnquoteSplicingForm -> TODO()
        }
    }

    fun analyseValueExpr(forms: List<Form>): ValueExpr = doAnalyser(AnalyserState(forms))
}