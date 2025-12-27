package brj.types

import brj.*

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
        is LetExpr -> TODO()
        is FnExpr -> TODO()
        is CallExpr -> TODO()
        is DoExpr -> TODO()
        is IfExpr -> typing()
        is CaseExpr -> TODO()
        is QuoteExpr -> TODO()
        is TruffleObjectExpr -> TODO()
        is HostStaticMethodExpr -> TODO()
        is HostConstructorExpr -> TODO()
    }
