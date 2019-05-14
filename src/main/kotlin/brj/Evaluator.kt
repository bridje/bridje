package brj

import brj.analyser.*
import java.util.*

internal interface NSFormLoader {
    fun loadNSForms(ns: Symbol): List<Form>
}

internal interface Emitter {
    fun evalValueExpr(expr: ValueExpr): Any
    fun emitJavaImport(javaImport: JavaImport): Any
    fun emitRecordKey(recordKey: RecordKey): Any
    fun emitVariantKey(variantKey: VariantKey): Any
    fun evalEffectExpr(sym: QSymbol, defaultImpl: BridjeFunction?): Any
}

internal interface MacroEvaluator {
    fun evalMacro(env: RuntimeEnv, macroVar: DefMacroVar, argForms: List<Form>): Form
}

internal class Evaluator(var env: RuntimeEnv,
                         private val loader: NSFormLoader,
                         private val emitter: Emitter,
                         private val macroEvaluator: MacroEvaluator) {

    private inner class NSEvaluator(var nsEnv: NSEnv) {
        private fun evalForm(form: Form) {
            val result = ExprAnalyser(env, nsEnv, macroEvaluator).analyseExpr(form)

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

                        is DefMacroExpr -> {
                            if (expr.type.effects.isNotEmpty()) TODO()
                            nsEnv += DefMacroVar(expr.sym, expr.type, emitter.evalValueExpr(expr.expr))
                        }

                        is VarDeclExpr -> nsEnv +=
                            if (expr.type.effects == setOf(expr.sym))
                                EffectVar(expr.sym, expr.type, false, emitter.evalEffectExpr(expr.sym, defaultImpl = null))
                            else
                                DefVar(expr.sym, expr.type, null)

                        is PolyVarDeclExpr -> nsEnv += PolyVar(expr.sym, expr.typeVar, expr.type)
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

        fun evalNS(forms: List<Form>) {
            forms.forEach(this::evalForm)
        }
    }

    internal data class NSFile(val nsEnv: NSEnv, val forms: List<Form>)

    private fun nsDeps(nsEnv: NSEnv): Set<Symbol> =
        nsEnv.refers.values.mapTo(mutableSetOf()) { it.ns } + nsEnv.aliases.values.mapNotNull { (it as? BridjeAlias)?.ns }.toSet()

    private fun nsAnalyser(ns: Symbol) =
        NSAnalyser(ns)

    private fun loadNSForms(rootNSes: Set<Symbol>): List<NSFile> {
        val stack = LinkedHashSet<Symbol>()

        val res = LinkedList<NSFile>()
        val seen = mutableSetOf<Symbol>()

        fun loadNS(ns: Symbol) {
            if (seen.contains(ns)) return
            if (stack.contains(ns)) throw TODO("Cyclic NS")

            stack += ns
            seen += ns

            val state = ParserState(loader.loadNSForms(ns))
            val nsEnv = nsAnalyser(ns).analyseNS(state.expectForm())

            (nsDeps(nsEnv) - seen).forEach(::loadNS)

            res.add(NSFile(nsEnv, state.forms))

            stack -= ns
        }

        rootNSes.forEach(::loadNS)

        return res
    }

    fun requireNSes(rootNSes: Set<Symbol>) {
        loadNSForms(rootNSes).forEach { NSEvaluator(it.nsEnv).evalNS(it.forms) }
    }
}