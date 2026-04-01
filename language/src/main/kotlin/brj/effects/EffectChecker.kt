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
    is TryCatchExpr -> {
        val bodyEffects = bodyExpr.inferEffects()
        val catchEffects = catchBranches.flatMap { it.bodyExpr.inferEffects() }.toSet()
        val finallyEffects = finallyExpr?.inferEffects() ?: emptySet()
        bodyEffects + catchEffects + finallyEffects
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

/**
 * Collects the set of GlobalVars that appear as callees in CallExpr nodes
 * where the callee has non-empty effects (i.e. effectful function calls that
 * need two-stage invocation). Recurses through FnExpr bodies but stops at
 * WithFxExpr boundaries (those get their own pre-application scope).
 */
fun ValueExpr.collectEffectfulCallees(): Set<GlobalVar> = when (this) {
    is CallExpr -> {
        val callee = when (fnExpr) {
            is GlobalVarExpr -> if (fnExpr.globalVar.effects.isNotEmpty()) setOf(fnExpr.globalVar) else emptySet()
            else -> emptySet()
        }
        callee + fnExpr.collectEffectfulCallees() + argExprs.flatMap { it.collectEffectfulCallees() }
    }
    is WithFxExpr -> bindings.flatMap { it.second.collectEffectfulCallees() }.toSet()
    is FnExpr -> bodyExpr.collectEffectfulCallees()
    is LetExpr -> bindingExpr.collectEffectfulCallees() + bodyExpr.collectEffectfulCallees()
    is DoExpr -> (sideEffects + listOf(result)).flatMap { it.collectEffectfulCallees() }.toSet()
    is IfExpr -> predExpr.collectEffectfulCallees() + thenExpr.collectEffectfulCallees() + elseExpr.collectEffectfulCallees()
    is CaseExpr -> scrutinee.collectEffectfulCallees() + branches.flatMap { it.bodyExpr.collectEffectfulCallees() }
    is TryCatchExpr -> bodyExpr.collectEffectfulCallees() +
        catchBranches.flatMap { it.bodyExpr.collectEffectfulCallees() } +
        (finallyExpr?.collectEffectfulCallees() ?: emptySet())
    is RecordExpr -> fields.flatMap { it.second.collectEffectfulCallees() }.toSet()
    is RecordSetExpr -> recordExpr.collectEffectfulCallees() + valueExpr.collectEffectfulCallees()
    is VectorExpr -> els.flatMap { it.collectEffectfulCallees() }.toSet()
    is SetExpr -> els.flatMap { it.collectEffectfulCallees() }.toSet()
    is IntExpr, is DoubleExpr, is BigIntExpr, is BigDecExpr,
    is StringExpr, is BoolExpr, is NilExpr,
    is LocalVarExpr, is CapturedVarExpr, is GlobalVarExpr,
    is TruffleObjectExpr, is HostStaticMethodExpr, is HostConstructorExpr,
    is QuoteExpr, is EffectVarExpr, is ErrorValueExpr -> emptySet()
}
