package brj

import brj.analyser.*
import brj.emitter.BridjeFunction
import brj.emitter.QSymbol
import brj.emitter.QSymbol.Companion.mkQSym
import brj.emitter.Symbol
import brj.emitter.Symbol.Companion.mkSym
import brj.reader.Form
import brj.reader.NSForms
import brj.runtime.*
import brj.types.Type

internal interface Emitter {
    fun evalValueExpr(expr: ValueExpr): Any
    fun emitJavaImport(javaImport: JavaImport): Any
    fun emitRecordKey(recordKey: RecordKey): Any
    fun emitVariantKey(variantKey: VariantKey): Any
    fun evalEffectExpr(sym: QSymbol, defaultImpl: BridjeFunction?): Any
    fun emitDefMacroVar(expr: DefMacroExpr, formsNS: NSEnv, ns: Symbol): DefMacroVar
}

internal class Evaluator(var env: RuntimeEnv, private val emitter: Emitter) {

    private inner class NSEvaluator(var nsEnv: NSEnv) {
        fun nsQSym(sym: Symbol) = mkQSym(nsEnv.ns, sym)
        
        internal fun evalForm(form: Form) {
            when (val result = ExprAnalyser(nsEnv).analyseExpr(form)) {
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

                        is PolyVarDeclExpr -> nsEnv += PolyVar(
                            nsQSym(expr.sym),
                            expr.polyTypeVar,
                            Type(expr.type, mapOf(expr.polyTypeVar to setOf(nsQSym(expr.sym)))))

                        is PolyVarDefExpr -> nsEnv += PolyVarImpl(expr.polyVar, expr.implType, emitter.evalValueExpr(expr.expr))

                        is DefMacroExpr -> {
                            if (expr.type.effects.isNotEmpty()) TODO()
                            nsEnv += emitter.emitDefMacroVar(expr, env.nses[mkSym("brj.forms")]!!, nsEnv.ns)
                        }

                        is VarDeclExpr -> {
                            val qsym = nsQSym(expr.sym)
                            nsEnv +=
                                if (expr.isEffect)
                                    EffectVar(qsym, expr.type, false, emitter.evalEffectExpr(qsym, defaultImpl = null))
                                else
                                    DefVar(qsym, expr.type, null)
                        }

                        is TypeAliasDeclExpr -> nsEnv +=
                            (nsEnv.typeAliases[expr.sym] as? TypeAlias_)?.also { it.type = expr.type }
                                ?: TypeAlias_(mkQSym(nsEnv.ns, expr.sym), expr.typeVars, expr.type)

                        is RecordKeyDeclExpr -> {
                            val recordKey = RecordKey(nsQSym(expr.sym), expr.typeVars, expr.type)
                            nsEnv += RecordKeyVar(recordKey, emitter.emitRecordKey(recordKey))
                        }
                        is VariantKeyDeclExpr -> {
                            val variantKey = VariantKey(nsQSym(expr.sym), expr.typeVars, expr.paramTypes)
                            nsEnv += VariantKeyVar(variantKey, emitter.emitVariantKey(variantKey))
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