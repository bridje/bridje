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

internal fun CapturedVarExpr.typing(): Typing {
    val t = freshType()
    return Typing(t, mapOf(outerLocalVar to t))
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

    val paramLvSet = params.toSet()
    val capturedEnv = bodyTyping.monoEnv.filterKeys { it !in paramLvSet }
    val paramMonoEnv = bodyTyping.monoEnv.filterKeys { it in paramLvSet }

    val paramTypes = params.map { lv ->
        paramMonoEnv[lv] ?: freshType()
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

internal fun RecordExpr.typing(): Typing {
    val fieldTypings = fields.map { (_, v) -> v.typing() }

    return Typing.build(
        RecordType.notNull(),
        childTypings = fieldTypings,
        constraints = emptyList(),
    )
}

internal fun RecordSetExpr.typing(): Typing {
    val recordTyping = recordExpr.typing()
    val valueTyping = valueExpr.typing()

    return Typing.build(
        RecordType.notNull(),
        childTypings = listOf(recordTyping, valueTyping),
        constraints = listOf(recordTyping.type subOf RecordType.notNull()),
    )
}

internal fun SetExpr.typing(): Typing {
    val elemTypings = els.map { it.typing() }
    val elemType = freshType()

    return Typing.build(
        SetType(elemType).notNull(),
        childTypings = elemTypings,
        elemTypings.map { it.type subOf elemType },
    )
}

internal fun CaseExpr.typing(): Typing {
    val scrutineeTyping = scrutinee.typing()
    val resultType = freshType()

    val branchTypings = branches.map { branch ->
        val bodyTyping = branch.bodyExpr.typing()

        val patternBindings = when (val p = branch.pattern) {
            is TagPattern -> p.bindings.toSet()
            is CatchAllBindingPattern -> setOf(p.binding)
            is DefaultPattern, is NilPattern -> emptySet()
        }

        val strippedMonoEnv = bodyTyping.monoEnv.filterKeys { it !in patternBindings }

        val patternConstraints = when (val p = branch.pattern) {
            is CatchAllBindingPattern -> {
                val bindingReqmt = bodyTyping.monoEnv[p.binding]
                if (bindingReqmt != null) listOf(scrutineeTyping.type subOf bindingReqmt)
                else emptyList()
            }
            else -> emptyList()
        }

        Triple(Typing(bodyTyping.type, strippedMonoEnv), bodyTyping.type subOf resultType, patternConstraints)
    }

    return Typing.build(
        resultType,
        childTypings = listOf(scrutineeTyping) + branchTypings.map { it.first },
        constraints = branchTypings.map { it.second } + branchTypings.flatMap { it.third },
    )
}

internal fun TryCatchExpr.typing(): Typing {
    val bodyTyping = bodyExpr.typing()
    val resultType = freshType()

    val branchTypings = catchBranches.map { branch ->
        val branchBodyTyping = branch.bodyExpr.typing()

        val patternBindings = when (val p = branch.pattern) {
            is TagPattern -> p.bindings.toSet()
            is CatchAllBindingPattern -> setOf(p.binding)
            is DefaultPattern, is NilPattern -> emptySet()
        }

        val strippedMonoEnv = branchBodyTyping.monoEnv.filterKeys { it !in patternBindings }

        Pair(Typing(branchBodyTyping.type, strippedMonoEnv), branchBodyTyping.type subOf resultType)
    }

    val finallyTyping = finallyExpr?.typing()

    val childTypings = listOf(bodyTyping) + branchTypings.map { it.first } + listOfNotNull(finallyTyping)
    val constraints = listOf(bodyTyping.type subOf resultType) + branchTypings.map { it.second }

    return Typing.build(
        resultType,
        childTypings = childTypings,
        constraints = constraints,
    )
}

internal fun LoopExpr.typing(): Typing {
    val bindingTypings = bindings.map { (_, expr) -> expr.typing() }
    val bodyTyping = bodyExpr.typing()

    val constraints = bindings.zip(bindingTypings).mapNotNull { (binding, bindingTyping) ->
        val bodyReqmt = bodyTyping.monoEnv[binding.first]
        bodyReqmt?.let { bindingTyping.type subOf it }
    }

    val loopLvs = bindings.map { it.first }.toSet()
    val strippedBodyTyping = Typing(bodyTyping.type, bodyTyping.monoEnv.filterKeys { it !in loopLvs })

    return Typing.build(bodyTyping.type, listOf(strippedBodyTyping) + bindingTypings, constraints)
}

internal fun RecurExpr.typing(): Typing {
    val argTypings = argExprs.map { it.typing() }

    val monoEnv = bindings.zip(argTypings).associate { (lv, argTyping) ->
        lv to argTyping.type
    }

    return Typing.build(nothingType(), argTypings, emptyList()).let {
        Typing(it.type, it.monoEnv + monoEnv)
    }
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
        is SetExpr -> typing()
        is RecordExpr -> typing()
        is LocalVarExpr -> typing()
        is CapturedVarExpr -> typing()
        is GlobalVarExpr -> Typing(globalVar.type?.instantiate() ?: freshType())
        is LetExpr -> typing()
        is FnExpr -> typing()
        is CallExpr -> typing()
        is DoExpr -> typing()
        is IfExpr -> typing()
        is CaseExpr -> typing()
        is TryCatchExpr -> typing()
        is QuoteExpr -> Typing(FormType.notNull())
        is TruffleObjectExpr -> Typing(freshType())
        is HostStaticMethodExpr -> Typing(freshType())
        is HostConstructorExpr -> Typing(freshType())
        is RecordSetExpr -> typing()
        is EffectVarExpr -> Typing(effectVar.type?.instantiate() ?: freshType())
        is WithFxExpr -> {
            val bindingTypings = bindings.map { (_, expr) -> expr.typing() }
            val bodyTyping = bodyExpr.typing()
            Typing.build(
                bodyTyping.type,
                childTypings = bindingTypings + listOf(bodyTyping),
                constraints = emptyList(),
            )
        }
        is LoopExpr -> typing()
        is RecurExpr -> typing()
        is ErrorValueExpr -> Typing(freshType())
    }
