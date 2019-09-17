package brj

import brj.analyser.*
import brj.emitter.BridjeFunction
import brj.emitter.QSymbol
import brj.emitter.QSymbol.Companion.mkQSym
import brj.emitter.Symbol
import brj.reader.Form
import brj.reader.NSForms
import brj.runtime.*

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
        fun nsQSym(sym: Symbol) = mkQSym(nsEnv.ns, sym)
        
        internal fun evalForm(form: Form) {
            when (val result = ExprAnalyser(nsEnv.ns, nsEnv).analyseExpr(form)) {
                is DoResult -> result.forms.forEach(this::evalForm)

                is ExprResult -> {
                    when (val expr = result.expr) {
                        is DefExpr -> {
                            val qSym = nsQSym(expr.sym)

                            val valueExpr =
                                if (expr.type.effects.isNotEmpty())
                                    (expr.expr as FnExpr).let { it.copy(params = listOf(DEFAULT_EFFECT_LOCAL) + it.params) }
                                else expr.expr

                            val value = emitter.evalValueExpr(valueExpr)

                            nsEnv +=
                                if (expr.isEffect || nsEnv.vars[expr.sym] is EffectVar)
                                    EffectVar(qSym, expr.type, true, emitter.evalEffectExpr(qSym, value as BridjeFunction))
                                else
                                    DefVar(qSym, expr.type, value)
                        }

                        is PolyVarDeclExpr -> nsEnv += expr.polyVar
                        is PolyVarDefExpr -> nsEnv += PolyVarImpl(expr.polyVar, expr.implType, emitter.evalValueExpr(expr.expr))

                        is DefMacroExpr -> {
                            if (expr.type.effects.isNotEmpty()) TODO()
                            nsEnv += emitter.emitDefMacroVar(expr)
                        }

                        is VarDeclExpr -> nsEnv +=
                            if (expr.isEffect)
                                EffectVar(expr.sym, expr.type, false, emitter.evalEffectExpr(expr.sym, defaultImpl = null))
                            else
                                DefVar(expr.sym, expr.type, null)

                        is TypeAliasDeclExpr -> nsEnv +=
                            (nsEnv.typeAliases[expr.sym] as? TypeAlias_)?.also { it.type = expr.type }
                                ?: TypeAlias_(mkQSym(nsEnv.ns, expr.sym), expr.typeVars, expr.type)

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
        val evaluator = NSEvaluator(NSEnv.create(env, nsForms.nsHeader))

        nsForms.forms.forEach(evaluator::evalForm)
    }

}