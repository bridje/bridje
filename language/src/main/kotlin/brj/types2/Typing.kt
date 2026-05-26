package brj.types2

import brj.analyser.BoolExpr
import brj.analyser.BigDecExpr
import brj.analyser.BigIntExpr
import brj.analyser.DoubleExpr
import brj.analyser.FnExpr
import brj.analyser.IfExpr
import brj.analyser.IntExpr
import brj.analyser.LocalVar
import brj.analyser.LocalVarExpr
import brj.analyser.StringExpr
import brj.analyser.ValueExpr

internal typealias MonoEnv = Map<LocalVar, Type>

internal data class Typing(val type: Type, val monoEnv: MonoEnv = emptyMap()) {
    companion object {
        fun build(
            outputType: Type,
            childTypings: Collection<Typing>,
            constraints: Collection<Constraint>,
        ): Typing {
            val grouped: Map<LocalVar, Pair<Type, List<Type>>> =
                childTypings.flatMap { it.monoEnv.entries }
                    .groupBy { it.key }
                    .mapValues { (_, entries) ->
                        val types = entries.map { it.value }
                        if (types.size == 1) Pair(types.single(), types)
                        else Pair(freshSlot(), types)
                    }

            val mergedConstraints = grouped.values.flatMap { (slot, types) ->
                types.map { Constraint(slot, it) }
            }

            val substs = (constraints + mergedConstraints).resolve()

            return Typing(
                outputType.applySubst(substs),
                grouped.mapValues { (_, v) -> v.first.applySubst(substs) }
            )
        }
    }
}

internal fun freshSlot(): Type =
    Type(TypeVarType(TypeVar()), Nullability(Nullability.Value.NOT_NULL, NullabilityVar()))

private fun primitive(base: BaseType): Type =
    Type(base, Nullability(Nullability.Value.NOT_NULL, null))

internal fun LocalVarExpr.typing(): Typing {
    val slot = freshSlot()
    return Typing(slot, mapOf(localVar to slot))
}

internal fun FnExpr.typing(): Typing {
    val bodyTyping = bodyExpr.typing()

    val paramLvSet = params.toSet()
    val capturedEnv = bodyTyping.monoEnv.filterKeys { it !in paramLvSet }
    val paramMonoEnv = bodyTyping.monoEnv.filterKeys { it in paramLvSet }

    val paramTypes = params.map { lv ->
        paramMonoEnv[lv] ?: freshSlot()
    }

    val fnType = Type(FnType(paramTypes, bodyTyping.type), Nullability(Nullability.Value.NOT_NULL, null))
    return Typing(fnType, capturedEnv)
}

internal fun IfExpr.typing(): Typing {
    val predTyping = predExpr.typing()
    val thenTyping = thenExpr.typing()
    val elseTyping = elseExpr.typing()
    val resultSlot = freshSlot()

    return Typing.build(
        resultSlot,
        childTypings = listOf(predTyping, thenTyping, elseTyping),
        constraints = listOf(
            Constraint(predTyping.type, primitive(BoolType)),
            Constraint(thenTyping.type, resultSlot),
            Constraint(elseTyping.type, resultSlot),
        ),
    )
}

internal fun ValueExpr.typing(): Typing = when (this) {
    is IntExpr -> Typing(primitive(IntType))
    is DoubleExpr -> Typing(primitive(DoubleType))
    is BigIntExpr -> Typing(primitive(BigIntType))
    is BigDecExpr -> Typing(primitive(BigDecType))
    is StringExpr -> Typing(primitive(StringType))
    is BoolExpr -> Typing(primitive(BoolType))
    is LocalVarExpr -> typing()
    is FnExpr -> typing()
    is IfExpr -> typing()
    else -> throw NotImplementedError("brj.types2: unhandled expr ${this::class.simpleName}")
}

fun ValueExpr.checkType2(): Type = typing().type
