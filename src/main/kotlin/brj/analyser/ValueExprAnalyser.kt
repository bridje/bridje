package brj.analyser

import brj.*
import brj.Symbol.Companion.mkSym
import brj.SymbolKind.RECORD_KEY_SYM
import brj.SymbolKind.VAR_SYM
import java.math.BigDecimal
import java.math.BigInteger

class LocalVar(val sym: Symbol) {
    override fun toString() = "LV($sym)"
}

sealed class ValueExpr

data class BooleanExpr(val boolean: Boolean) : ValueExpr()
data class StringExpr(val string: String) : ValueExpr()
data class IntExpr(val int: Long) : ValueExpr()
data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
data class FloatExpr(val float: Double) : ValueExpr()
data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()

data class QuotedSymbolExpr(val sym: Symbol) : ValueExpr()
data class QuotedQSymbolExpr(val sym: QSymbol) : ValueExpr()

data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()
data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()
data class RecordEntry(val recordKey: RecordKey, val expr: ValueExpr)
data class RecordExpr(val entries: List<RecordEntry>) : ValueExpr()

data class CallExpr(val f: ValueExpr, val effectArg: LocalVarExpr?, val args: List<ValueExpr>) : ValueExpr()
data class FnExpr(val fnName: Symbol? = null, val params: List<LocalVar>, val expr: ValueExpr) : ValueExpr()

data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()
data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

data class LetBinding(val localVar: LocalVar, val expr: ValueExpr)
data class LetExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()

data class LoopExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()
data class RecurExpr(val exprs: List<Pair<LocalVar, ValueExpr>>) : ValueExpr()

data class CaseClause(val variantKey: VariantKey, val bindings: List<LocalVar>, val bodyExpr: ValueExpr)
data class CaseExpr(val expr: ValueExpr, val clauses: List<CaseClause>, val defaultExpr: ValueExpr?) : ValueExpr()

data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()
data class GlobalVarExpr(val globalVar: GlobalVar) : ValueExpr()

data class EffectDef(val effectVar: EffectVar, val fnExpr: FnExpr)
data class WithFxExpr(val oldFxLocal: LocalVar,
                      val fx: Set<EffectDef>,
                      val newFxLocal: LocalVar,
                      val bodyExpr: ValueExpr) : ValueExpr()

internal val IF = mkSym("if")
internal val FN = mkSym("fn")
internal val LET = mkSym("let")
internal val CASE = mkSym("case")
internal val LOOP = mkSym("loop")
internal val RECUR = mkSym("recur")
internal val WITH_FX = mkSym("with-fx")

internal val DEFAULT_EFFECT_LOCAL = LocalVar(mkSym("_fx"))

@Suppress("NestedLambdaShadowedImplicitParameter")
internal data class ValueExprAnalyser(val env: Env, val nsEnv: NSEnv,
                                      val locals: Map<Symbol, LocalVar> = emptyMap(),
                                      val loopLocals: List<LocalVar>? = null,
                                      val effectLocal: LocalVar = DEFAULT_EFFECT_LOCAL) {
    private fun resolve(ident: Ident) = resolve(env, nsEnv, ident)

    private fun symAnalyser(form: SymbolForm): ValueExpr =
        (locals[form.sym]?.let { LocalVarExpr(it) })
            ?: resolve(form.sym)?.let(::GlobalVarExpr)
            ?: TODO("sym not found: ${form.sym}")

    private fun qsymAnalyser(form: QSymbolForm): ValueExpr =
        resolve(form.sym)?.let(::GlobalVarExpr)
            ?: TODO("sym not found: ${form.sym}")

    private fun ifAnalyser(it: ParserState): ValueExpr {
        val predExpr = exprAnalyser(it)
        val thenExpr = exprAnalyser(it)
        val elseExpr = exprAnalyser(it)
        it.expectEnd()
        return IfExpr(predExpr, thenExpr, elseExpr)
    }

    private fun letAnalyser(it: ParserState): ValueExpr {
        var ana = this
        return LetExpr(
            it.nested(VectorForm::forms) { bindingState ->
                bindingState.varargs {
                    val localVar = LocalVar(it.expectForm<SymbolForm>().sym)
                    val expr = ana.copy(loopLocals = null).exprAnalyser(it)

                    ana = ana.copy(locals = ana.locals.plus(localVar.sym to localVar))
                    LetBinding(localVar, expr)
                }
            },

            ana.exprAnalyser(it))
    }

    private fun loopAnalyser(it: ParserState): ValueExpr {
        val bindings = it.nested(VectorForm::forms) { bindingState ->
            val bindingCtx = this.copy(loopLocals = null)

            bindingState.varargs {
                LetBinding(LocalVar(it.expectForm<SymbolForm>().sym), bindingCtx.exprAnalyser(it))
            }
        }

        val ana = this.copy(locals = locals.plus(bindings.map { it.localVar.sym to it.localVar }), loopLocals = bindings.map { it.localVar })
        return LoopExpr(bindings, ana.exprAnalyser(it))
    }

    private fun recurAnalyser(it: ParserState): ValueExpr {
        if (loopLocals == null) TODO()

        val recurExprs = it.varargs(this::exprAnalyser)

        if (loopLocals.size != recurExprs.size) TODO()

        return RecurExpr(loopLocals.zip(recurExprs))
    }

    private fun fnAnalyser(it: ParserState): ValueExpr {
        val fnName: Symbol? = it.maybe { it.expectForm<SymbolForm>().sym }

        val paramNames = it.nested(VectorForm::forms) {
            it.varargs {
                it.expectForm<SymbolForm>().sym
            }
        }

        val newLocals = paramNames.map { it to LocalVar(it) }

        val ana = this.copy(locals = locals.plus(newLocals), loopLocals = newLocals.map { it.second })

        val bodyExpr = ana.doAnalyser(it)

        return FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr)
    }

    private fun callAnalyser(it: ParserState): ValueExpr {
        val fn = exprAnalyser(it)

        return if (fn is GlobalVarExpr && fn.globalVar is DefMacroVar) {
            TODO()
        } else {
            CallExpr(fn,
                (if (fn is GlobalVarExpr && fn.globalVar.type.effects.isNotEmpty()) LocalVarExpr(effectLocal) else null),
                it.varargs(::exprAnalyser))
        }
    }

    internal fun doAnalyser(it: ParserState): ValueExpr {
        val exprs = listOf(exprAnalyser(it)).plus(it.varargs(::exprAnalyser))
        return DoExpr(exprs.dropLast(1), exprs.last())
    }

    internal fun withFxAnalyser(it: ParserState): ValueExpr {
        data class Preamble(val sym: Symbol, val paramSyms: List<Symbol>)

        val fx = it.nested(VectorForm::forms) {
            it.varargs {
                it.nested(ListForm::forms) {
                    it.expectSym(DEF)

                    val preamble = it.nested(ListForm::forms) {
                        Preamble(it.expectSym(VAR_SYM), it.varargs { it.expectSym(VAR_SYM) })
                    }

                    val effectVar = resolve(preamble.sym) as? EffectVar ?: TODO()

                    val locals = preamble.paramSyms.map { it to LocalVar(it) }

                    val bodyExpr = this.copy(locals = locals.toMap(), loopLocals = locals.map { it.second }).doAnalyser(it)

                    val expr = FnExpr(preamble.sym, locals.map { it.second }, bodyExpr)

                    it.expectEnd()

                    EffectDef(effectVar, expr)
                }
            }
        }

        val newEffectLocal = LocalVar(DEFAULT_EFFECT_LOCAL.sym)

        return WithFxExpr(effectLocal, fx.toSet(), newEffectLocal, this.copy(effectLocal = newEffectLocal).doAnalyser(it))
    }

    private fun caseAnalyser(it: ParserState): ValueExpr {
        val expr = exprAnalyser(it)

        val clauses = mutableListOf<CaseClause>()

        while (it.forms.size > 1) {
            val clauseForm = it.expectForm<Form>()

            fun resolveVariantKey(form: Form): VariantKeyVar {
                return when (form) {
                    is SymbolForm -> resolve(form.sym)
                    else -> TODO()
                } as? VariantKeyVar ?: TODO()
            }

            val (variantKey, paramSyms) = when (clauseForm) {
                is SymbolForm -> Pair(resolveVariantKey(clauseForm).variantKey, emptyList())
                is ListForm -> {
                    it.nested(clauseForm.forms) {
                        Pair(resolveVariantKey(it.expectForm()).variantKey, it.varargs { it.expectForm<SymbolForm>().sym })
                    }
                }
                else -> TODO()
            }

            val localVars = paramSyms.map { it to LocalVar(it) }

            clauses += CaseClause(variantKey, localVars.map { it.second }, copy(locals = locals + localVars).exprAnalyser(it))
        }

        val defaultExpr = if (it.forms.isNotEmpty()) exprAnalyser(it) else null

        it.expectEnd()

        return CaseExpr(expr, clauses.toList(), defaultExpr)
    }

    private fun listAnalyser(it: ParserState): ValueExpr {
        if (it.forms.isEmpty()) throw ParseError.ExpectedForm

        val firstForm = it.forms[0]

        return if (firstForm is SymbolForm) {
            when (firstForm.sym) {
                IF, FN, LET, DO, CASE, LOOP, RECUR, WITH_FX -> it.forms = it.forms.drop(1)
            }

            when (firstForm.sym) {
                IF -> ifAnalyser(it)
                FN -> fnAnalyser(it)
                LET -> letAnalyser(it)
                DO -> doAnalyser(it)
                CASE -> caseAnalyser(it)
                LOOP -> loopAnalyser(it)
                RECUR -> recurAnalyser(it)
                WITH_FX -> withFxAnalyser(it)
                else -> callAnalyser(it)
            }
        } else {
            callAnalyser(it)
        }
    }

    private fun collAnalyser(transform: (List<ValueExpr>) -> ValueExpr): FormsParser<ValueExpr> = {
        transform(it.varargs(::exprAnalyser))
    }

    private fun recordAnalyser(form: RecordForm): ValueExpr {
        val entries = mutableListOf<RecordEntry>()

        val state = ParserState(form.forms)
        state.varargs {
            val attr = (resolve(it.expectIdent(RECORD_KEY_SYM)) as? RecordKeyVar)?.recordKey ?: TODO()

            entries += RecordEntry(attr, exprAnalyser(it))
        }

        return RecordExpr(entries)
    }

    private fun exprAnalyser(it: ParserState): ValueExpr {
        val form = it.expectForm<Form>()

        return when (form) {
            is BooleanForm -> BooleanExpr(form.bool)
            is StringForm -> StringExpr(form.string)
            is IntForm -> IntExpr(form.int)
            is BigIntForm -> BigIntExpr(form.bigInt)
            is FloatForm -> FloatExpr(form.float)
            is BigFloatForm -> BigFloatExpr(form.bigFloat)

            is SymbolForm -> symAnalyser(form)
            is QSymbolForm -> qsymAnalyser(form)

            is QuotedSymbolForm -> QuotedSymbolExpr(form.sym)
            is QuotedQSymbolForm -> QuotedQSymbolExpr(form.sym)

            is ListForm -> listAnalyser(ParserState(form.forms))
            is VectorForm -> collAnalyser(::VectorExpr)(ParserState(form.forms))
            is SetForm -> collAnalyser(::SetExpr)(ParserState(form.forms))
            is RecordForm -> recordAnalyser(form)
        }
    }

    fun analyseValueExpr(forms: List<Form>): ValueExpr = doAnalyser(ParserState(forms))
}

fun analyseValueExpr(env: Env, nsEnv: NSEnv, forms: List<Form>): ValueExpr = ValueExprAnalyser(env, nsEnv).analyseValueExpr(forms)
