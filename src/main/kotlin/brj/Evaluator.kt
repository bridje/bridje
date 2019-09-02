package brj

import brj.analyser.*

internal interface Emitter {
    fun evalValueExpr(expr: ValueExpr): Any
    fun emitJavaImport(javaImport: JavaImport): Any
    fun emitRecordKey(recordKey: RecordKey): Any
    fun emitVariantKey(variantKey: VariantKey): Any
    fun evalEffectExpr(sym: QSymbol, defaultImpl: BridjeFunction?): Any
    fun emitDefMacroVar(expr: DefMacroExpr): DefMacroVar
}

internal class Evaluator(var env: RuntimeEnv, private val emitter: Emitter) {

    private inner class NSEvaluator(var nsEnv: NSEnv) {
        internal fun evalForm(form: Form) {
            val result = ExprAnalyser(env, nsEnv).analyseExpr(form)

            when (result) {
                is DoResult -> result.forms.forEach(this::evalForm)

                is ExprResult -> {
                    val expr = result.expr

                    when (expr) {
                        is DefExpr -> {
                            val valueExpr =
                                if (expr.type.effects.isNotEmpty())
                                    (expr.expr as FnExpr).let { it.copy(params = listOf(DEFAULT_EFFECT_LOCAL) + it.params) }
                                else expr.expr

                            val value = emitter.evalValueExpr(valueExpr)

                            nsEnv +=
                                if (expr.type.effects == setOf(expr.sym))
                                    EffectVar(expr.sym, expr.type, true, emitter.evalEffectExpr(expr.sym, value as BridjeFunction))
                                else
                                    DefVar(expr.sym, expr.type, value)
                        }

                        is PolyVarDeclExpr -> nsEnv += expr.polyVar
                        is PolyVarDefExpr -> nsEnv += PolyVarImpl(expr.polyVar, expr.implType, emitter.evalValueExpr(expr.expr))

                        is DefMacroExpr -> {
                            if (expr.type.effects.isNotEmpty()) TODO()
                            nsEnv += emitter.emitDefMacroVar(expr)
                        }

                        is VarDeclExpr -> nsEnv +=
                            if (expr.type.effects == setOf(expr.sym))
                                EffectVar(expr.sym, expr.type, false, emitter.evalEffectExpr(expr.sym, defaultImpl = null))
                            else
                                DefVar(expr.sym, expr.type, null)

                        is TypeAliasDeclExpr -> nsEnv += expr.typeAlias
                        is RecordKeyDeclExpr -> nsEnv += RecordKeyVar(expr.recordKey, emitter.emitRecordKey(expr.recordKey))
                        is VariantKeyDeclExpr -> nsEnv += VariantKeyVar(expr.variantKey, emitter.emitVariantKey(expr.variantKey))
                        is JavaImportDeclExpr -> {
                            val ns = expr.javaImport.qsym.ns
                            env += (env.nses[ns] ?: NSEnv(ns)) +
                                JavaImportVar(expr.javaImport, emitter.emitJavaImport(expr.javaImport))
                        }
                    }
                }
            }

            env += nsEnv
        }
    }

    fun evalNS(nsForms: NSForms) {
        val evaluator = NSEvaluator(NSEnv(nsForms.nsHeader.ns, nsForms.nsHeader.refers, nsForms.nsHeader.aliases))
        nsForms.forms.forEach(evaluator::evalForm)
    }
}