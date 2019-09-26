package brj

import brj.analyser.*
import brj.emitter.BridjeFunction
import brj.reader.Form
import brj.reader.NSForms
import brj.runtime.*
import brj.runtime.QSymbol.Companion.mkQSym
import brj.types.PolyConstraint
import brj.types.Type

internal interface Emitter {
    fun evalValueExpr(expr: ValueExpr): Any
    fun emitJavaImport(javaImport: JavaImport): Any
    fun emitRecordKey(recordKey: RecordKey): Any
    fun emitVariantKey(variantKey: VariantKey): Any
    fun emitEffectFn(sym: QSymbol): Any
    fun emitDefMacroVar(expr: DefMacroExpr, ns: Symbol): DefMacroVar
    fun emitPolyVar(polyConstraint: PolyConstraint): Any
}

internal class Evaluator(private val emitter: Emitter) {
    private inner class NSEvaluator(val env: RuntimeEnv, val nsHeader: NSHeader) {
        fun nsQSym(sym: Symbol) = mkQSym(nsHeader.ns, sym)

        private val resolver = Resolver.NSResolver.create(env, nsHeader)

        internal fun evalForm(nsEnv: NSEnv, form: Form): NSEnv =
            when (val result = ExprAnalyser(resolver.copy(nsEnv = nsEnv)).analyseExpr(form)) {
                is DoResult -> result.forms.fold(nsEnv, this::evalForm)

                is ExprResult -> {
                    when (val expr = result.expr) {
                        is DefExpr -> {
                            val qSym = nsQSym(expr.sym)

                            val value = emitter.evalValueExpr(expr.expr)

                            nsEnv +
                                if (nsEnv.vars[expr.sym] is EffectVar)
                                    EffectVar(qSym, expr.type.copy(effects = setOf(qSym)), value as BridjeFunction, emitter.emitEffectFn(qSym))
                                else
                                    DefVar(qSym, expr.type, value)
                        }

                        is PolyVarDeclExpr -> {
                            val polyConstraint = PolyConstraint(nsQSym(expr.sym), expr.primaryTVs, expr.secondaryTVs)
                            nsEnv + PolyVar(polyConstraint, expr.type, emitter.emitPolyVar(polyConstraint))
                        }

                        is PolyVarDefExpr -> nsEnv + PolyVarImpl(expr.polyVar, expr.primaryPolyTypes, expr.secondaryPolyTypes, emitter.evalValueExpr(expr.expr))

                        is DefMacroExpr -> {
                            if (expr.type.effects.isNotEmpty()) TODO()
                            nsEnv + emitter.emitDefMacroVar(expr, nsEnv.ns)
                        }

                        is VarDeclExpr -> {
                            val qsym = nsQSym(expr.sym)
                            nsEnv +
                                if (expr.isEffect)
                                    EffectVar(qsym, expr.type, defaultImpl = null, value = emitter.emitEffectFn(qsym))
                                else
                                    DefVar(qsym, expr.type, null)
                        }

                        is TypeAliasDeclExpr -> nsEnv +
                            ((nsEnv.typeAliases[expr.sym] as? TypeAlias_)?.also { it.type = expr.type }
                                ?: TypeAlias_(mkQSym(nsEnv.ns, expr.sym), expr.typeVars, expr.type))

                        is RecordKeyDeclExpr -> {
                            val recordKey = RecordKey(nsQSym(expr.sym), expr.typeVars, expr.type)
                            nsEnv + RecordKeyVar(recordKey, emitter.emitRecordKey(recordKey))
                        }
                        is VariantKeyDeclExpr -> {
                            val variantKey = VariantKey(nsQSym(expr.sym), expr.typeVars, expr.paramTypes)
                            nsEnv + VariantKeyVar(variantKey, emitter.emitVariantKey(variantKey))
                        }
                    }
                }
            }
    }

    fun evalNS(env: RuntimeEnv, nsForms: NSForms): RuntimeEnv {
        val javaImportNSEnvs = nsForms.nsHeader.aliases.values.mapNotNull { alias ->
            when (alias) {
                is JavaAlias -> {
                    NSEnv(alias.ns,
                        vars = alias.decls.mapValues { (_, decl) ->
                            val javaImport = JavaImport(mkQSym(alias.ns, decl.sym), alias.clazz, decl.sym.baseStr, Type(decl.type))
                            JavaImportVar(javaImport, emitter.emitJavaImport(javaImport))
                        })
                }
                is BridjeAlias -> null
            }
        }

        val nsEvaluator = NSEvaluator(env + javaImportNSEnvs, nsForms.nsHeader)
        return nsEvaluator.env + nsForms.forms.fold(NSEnv(nsForms.nsHeader.ns), nsEvaluator::evalForm)
    }
}