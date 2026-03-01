package brj.types

import brj.analyser.*

internal data class Typing(
    val type: Type,
    val monoEnv: MonoEnv = emptyMap(),
) {
    companion object {
        fun build(
            outputType: Type,
            childTypings: Collection<Typing>,
            constraints: Collection<Constraint>,
        ): Typing {
            val monoEnvReqmts = childTypings.map { it.monoEnv }.groupReqmts()

            val monoEnvConstraints =
                monoEnvReqmts.values
                     .flatMap { (tvt, types) ->
                         types.map { tvt subOf it }
                     }

            val subst = constraints.plus(monoEnvConstraints).resolve()

            return Typing(
                outputType.applySubst(subst),
                monoEnvReqmts.mapValues { it.value.first.applySubst(subst) }
            )
        }
    }
}

internal fun LocalVarExpr.typing(): Typing {
    val t = freshType()
    return Typing(t, mapOf(localVar to t))
}

internal fun VectorExpr.typing(): Typing {
    val elemTypings = els.map { it.typing() }
    val elemType = freshType()

    return Typing.build(
        VectorType(elemType).notNull(),
        childTypings = elemTypings,
        elemTypings.map { it.type subOf elemType },
    )
}

internal fun IfExpr.typing(): Typing {
    val predTyping = predExpr.typing()
    val thenTyping = thenExpr.typing()
    val elseTyping = elseExpr.typing()
    val resultType = freshType()

    return Typing.build(
        resultType,
        childTypings = listOf(predTyping, thenTyping, elseTyping),
        constraints = listOf(
            predTyping.type subOf BoolType.notNull(),
            thenTyping.type subOf resultType,
            elseTyping.type subOf resultType,
        ),
    )
}

internal fun DoExpr.typing(): Typing {
    val allTypings = sideEffects.map { it.typing() } + result.typing()
    val resultTyping = allTypings.last()

    return Typing.build(
        resultTyping.type,
        childTypings = allTypings,
        constraints = emptyList(),
    )
}

internal fun LetExpr.typing(): Typing {
    val bindingTyping = bindingExpr.typing()
    val bodyTyping = bodyExpr.typing()

    val constraints = mutableListOf<Constraint>()
    val bodyReqmt = bodyTyping.monoEnv[localVar]
    if (bodyReqmt != null) {
        constraints.add(bindingTyping.type subOf bodyReqmt)
    }

    val strippedBodyTyping = Typing(bodyTyping.type, bodyTyping.monoEnv.minus(localVar))

    return Typing.build(
        bodyTyping.type,
        childTypings = listOf(bindingTyping, strippedBodyTyping),
        constraints = constraints,
    )
}

internal fun FnExpr.typing(): Typing {
    val bodyTyping = bodyExpr.typing()

    val paramLocalVars = bodyTyping.monoEnv.keys.filter { it.slot < params.size }
    val capturedEnv = bodyTyping.monoEnv.filterKeys { it.slot >= params.size }

    val paramTypes = (0 until params.size).map { slot ->
        paramLocalVars.find { it.slot == slot }?.let { bodyTyping.monoEnv[it] } ?: freshType()
    }

    val fnType = FnType(paramTypes, bodyTyping.type).notNull()
    return Typing(fnType, capturedEnv)
}

internal fun CallExpr.typing(): Typing {
    val fnTyping = fnExpr.typing()
    val argTypings = argExprs.map { it.typing() }

    val freshReturn = freshType()

    return Typing.build(
        freshReturn,
        childTypings = listOf(fnTyping) + argTypings,
        constraints = listOf(
            fnTyping.type subOf FnType(argTypings.map { it.type }, freshReturn).notNull()
        ),
    )
}

internal fun ValueExpr.typing(): Typing =
    when (this) {
        is IntExpr -> Typing(IntType.notNull())
        is DoubleExpr -> Typing(FloatType.notNull())
        is BigIntExpr -> Typing(BigIntType.notNull())
        is BigDecExpr -> Typing(BigDecType.notNull())
        is StringExpr -> Typing(StringType.notNull())
        is BoolExpr -> Typing(BoolType.notNull())
        is NilExpr -> Typing(nullType())
        is VectorExpr -> typing()
        is SetExpr -> TODO()
        is RecordExpr -> TODO()
        is LocalVarExpr -> typing()
        is GlobalVarExpr -> TODO()
        is LetExpr -> typing()
        is FnExpr -> typing()
        is CallExpr -> typing()
        is DoExpr -> typing()
        is IfExpr -> typing()
        is CaseExpr -> TODO()
        is QuoteExpr -> TODO()
        is TruffleObjectExpr -> TODO()
        is HostStaticMethodExpr -> TODO()
        is HostConstructorExpr -> TODO()
        is RecordSetExpr -> TODO()
        is ErrorValueExpr -> TODO()
    }
