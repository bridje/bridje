package brj.effects

import brj.GlobalVar
import brj.analyser.*

fun ValueExpr.inferEffects(): Set<GlobalVar> = when (this) {
    is EffectVarExpr -> setOf(effectVar)
    is CallExpr -> {
        val fnEffects = fnExpr.inferEffects()
        val argEffects = argExprs.flatMap { it.inferEffects() }.toSet()
        val calleeEffects = when (fnExpr) {
            is GlobalVarExpr -> fnExpr.globalVar.effects.toSet()
            else -> emptySet()
        }
        fnEffects + argEffects + calleeEffects
    }
    is WithFxExpr -> {
        val bodyEffects = bodyExpr.inferEffects()
        val providedVars = bindings.map { it.first }.toSet()
        val bindingEffects = bindings.flatMap { it.second.inferEffects() }.toSet()
        (bodyEffects - providedVars) + bindingEffects
    }
    is FnExpr -> bodyExpr.inferEffects()
    is LetExpr -> bindingExpr.inferEffects() + bodyExpr.inferEffects()
    is DoExpr -> (sideEffects + listOf(result)).flatMap { it.inferEffects() }.toSet()
    is IfExpr -> predExpr.inferEffects() + thenExpr.inferEffects() + elseExpr.inferEffects()
    is CaseExpr -> {
        val scrutEffects = scrutinee.inferEffects()
        val branchEffects = branches.flatMap { it.bodyExpr.inferEffects() }.toSet()
        scrutEffects + branchEffects
    }
    is RecordExpr -> fields.flatMap { it.second.inferEffects() }.toSet()
    is RecordSetExpr -> recordExpr.inferEffects() + valueExpr.inferEffects()
    is VectorExpr -> els.flatMap { it.inferEffects() }.toSet()
    is SetExpr -> els.flatMap { it.inferEffects() }.toSet()
    is IntExpr, is DoubleExpr, is BigIntExpr, is BigDecExpr,
    is StringExpr, is BoolExpr, is NilExpr,
    is LocalVarExpr, is CapturedVarExpr, is GlobalVarExpr,
    is TruffleObjectExpr, is HostStaticMethodExpr, is HostConstructorExpr,
    is QuoteExpr, is ErrorValueExpr -> emptySet()
}
