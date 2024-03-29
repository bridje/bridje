package brj

import brj.runtime.Symbol

data class Typing(
    val res: MonoType,
    val monoEnv: Map<LocalVar, MonoType> = emptyMap(),
    val fx: Set<Symbol> = emptySet(),
) {
    override fun toString() = "(Typing $monoEnv, $fx => $res)"
}

private data class Constraint(val leftType: MonoType, val rightType: MonoType)

private fun zipConstraints(lefts: List<MonoType>, rights: List<MonoType>): Set<Constraint> =
    lefts.zip(rights).map { Constraint(it.first, it.second) }.toSet()

private typealias Mapping = Map<TypeVar, MonoType>

private fun MonoType.apply(mapping: Mapping): MonoType = when (this) {
    BoolType, IntType, StringType -> this
    is TypeVar -> mapping.getOrDefault(this, this)
    is FnType -> FnType(paramTypes.map { it.apply(mapping) }, resType.apply(mapping))
    is SetType -> SetType(elType.apply(mapping))
    is VectorType -> VectorType(elType.apply(mapping))
}

private fun Typing.instantiate(): Typing {
    val tvMapping: MutableMap<TypeVar, TypeVar> = mutableMapOf()

    fun MonoType.instantiate(): MonoType = when (this) {
        BoolType, IntType, StringType -> this
        is FnType -> FnType(paramTypes.map(MonoType::instantiate), resType.instantiate())
        is SetType -> SetType(elType.instantiate())
        is VectorType -> VectorType(elType.instantiate())
        is TypeVar -> tvMapping.getOrElse(this) { TypeVar(s) }
    }

    return Typing(
        res.instantiate(),
        monoEnv.asIterable().associate { it.key to it.value.instantiate() },
        fx
    )
}

private fun Constraint.apply(mapping: Mapping) =
    Constraint(leftType.apply(mapping), rightType.apply(mapping))

private fun <K> Map<K, MonoType>.apply(mapping: Mapping) =
    this.asIterable().associate { it.key to it.value.apply(mapping) }

private fun combineTypings(
    res: MonoType,
    typings: Iterable<Typing> = emptyList(),
    constraints: Iterable<Constraint> = emptySet(),
    monoEnv: Map<LocalVar, MonoType> = emptyMap()
): Typing {
    val constraintQueue = constraints.toMutableList()
    var mapping = emptyMap<TypeVar, MonoType>()

    val combinedMonoEnv = monoEnv.toList() + typings.flatMap { it.monoEnv.toList() }

    val localVarTypeVars: Map<LocalVar, MonoType> = combinedMonoEnv
        .asIterable().associate { it.first to TypeVar(it.first.symbol.name) }

    constraintQueue.addAll(combinedMonoEnv.map { Constraint(it.second, localVarTypeVars[it.first]!!) })

    fun unifyConstraint(c: Constraint): Mapping =
        if (c.rightType is TypeVar) {
            mapOf(c.rightType to c.leftType)
        } else {
            when (c.leftType) {
                is TypeVar -> mapOf(c.leftType to c.rightType)
                IntType, BoolType, StringType -> if (c.leftType == c.rightType) emptyMap() else null
                is FnType -> if (c.rightType is FnType && c.leftType.paramTypes.size == c.rightType.paramTypes.size) {
                    constraintQueue.addAll(zipConstraints(c.leftType.paramTypes, c.rightType.paramTypes))
                    constraintQueue.add(Constraint(c.leftType.resType, c.rightType.resType))
                    emptyMap()
                } else null
                is SetType -> if (c.rightType is SetType) {
                    constraintQueue.add(Constraint(c.leftType.elType, c.rightType.elType))
                    emptyMap()
                } else null
                is VectorType -> if (c.rightType is VectorType) {
                    constraintQueue.add(Constraint(c.leftType.elType, c.rightType.elType))
                    emptyMap()
                } else null
            } ?: TODO("failed to unify ${c.leftType} + ${c.rightType}")
        }

    while (constraintQueue.isNotEmpty()) {
        val newMapping = unifyConstraint(constraintQueue.removeFirst())

        mapping = mapping.apply(newMapping).plus(newMapping)

        constraintQueue.forEachIndexed { idx, c ->
            constraintQueue[idx] = c.apply(mapping)
        }
    }

    return Typing(res.apply(mapping), localVarTypeVars.apply(mapping), typings.flatMap(Typing::fx).toSet())
}

private class TypeChecker(val localPolyEnv: Map<LocalVar, Typing> = emptyMap()) {
    private fun primitiveTyping(type: MonoType) = Typing(type)

    private fun collTyping(mkType: (MonoType) -> MonoType, exprs: List<ValueExpr>): Typing {
        val typings = exprs.map(::valueExprTyping)
        val elTypeVar = TypeVar("el")

        return combineTypings(
            mkType(elTypeVar),
            typings,
            typings.mapTo(mutableSetOf()) { Constraint(it.res, elTypeVar) })
    }

    private fun doTyping(doExpr: DoExpr): Typing {
        val returnExprTyping = valueExprTyping(doExpr.expr)
        return combineTypings(
            returnExprTyping.res,
            doExpr.exprs.map(::valueExprTyping) + returnExprTyping
        )
    }

    private fun ifTyping(ifExpr: IfExpr): Typing {
        val predTyping = valueExprTyping(ifExpr.predExpr)
        val thenTyping = valueExprTyping(ifExpr.thenExpr)
        val elseTyping = valueExprTyping(ifExpr.elseExpr)

        val resTypeVar = TypeVar()

        return combineTypings(
            resTypeVar,
            setOf(predTyping, thenTyping, elseTyping),
            setOf(
                Constraint(predTyping.res, BoolType),
                Constraint(thenTyping.res, resTypeVar),
                Constraint(elseTyping.res, resTypeVar)
            )
        )
    }

    private fun fnExprTyping(fnExpr: FnExpr): Typing {
        val exprTyping = valueExprTyping(fnExpr.expr)
        val typing = combineTypings(
            FnType(
                fnExpr.params.drop(1).map { exprTyping.monoEnv.getOrElse(it) { TypeVar() } },
                exprTyping.res
            ),
            listOf(exprTyping)
        )
        return typing.copy(monoEnv = typing.monoEnv - fnExpr.params)
    }

    private fun localVarExprTyping(localVar: LocalVar) =
        when (val typing = localPolyEnv[localVar]) {
            null -> {
                val typeVar = TypeVar(localVar.symbol.name)
                Typing(typeVar, mapOf(localVar to typeVar))
            }
            else -> typing.instantiate()
        }

    private fun globalVarExprTyping(expr: GlobalVarExpr) =
        expr.globalVar.typing.instantiate()

    private fun letExprTyping(expr: LetExpr): Typing {
        var typeChecker = this
        val bindingTypings = mutableListOf<Typing>()

        expr.bindings.forEach { binding ->
            val bindingTyping = typeChecker.valueExprTyping(binding.expr)
            bindingTypings.add(bindingTyping)
            typeChecker = TypeChecker(typeChecker.localPolyEnv + (binding.binding to bindingTyping))
        }

        val exprTyping = typeChecker.valueExprTyping(expr.expr)

        val letTyping = combineTypings(exprTyping.res, bindingTypings + exprTyping)

        return letTyping.copy(monoEnv = letTyping.monoEnv - expr.bindings.map { it.binding })
    }

    private fun callExprTyping(expr: CallExpr): Typing {
        val fnTyping = valueExprTyping(expr.fn)
        val paramTypings = expr.args.drop(1).map(::valueExprTyping)

        val fnType = when (fnTyping.res) {
            is FnType -> fnTyping.res
            is TypeVar -> FnType((paramTypings.indices).map { TypeVar("p${it}") }, TypeVar("r"))
            else -> TODO()
        }

        return combineTypings(
            fnType.resType,
            paramTypings + fnTyping,
            zipConstraints(paramTypings.map { it.res }, fnType.paramTypes) + Constraint(fnTyping.res, fnType)
        )
    }

    private fun withFxTyping(expr: WithFxExpr): Typing {
        val bindingTypings = expr.bindings.map { it.defxVar to valueExprTyping(it.expr) }
        val exprTyping = valueExprTyping(expr.expr)
        val typing = combineTypings(exprTyping.res,
            bindingTypings.map { it.second } + exprTyping,
            bindingTypings.map { Constraint(it.second.res, it.first.typing.res) })
        return typing.copy(fx = typing.fx - expr.bindings.map { it.defxVar.sym } + bindingTypings.flatMap { it.second.fx })
    }

    private fun loopExprTyping(expr: LoopExpr): Typing {
        val bindingTypings = expr.bindings.map {
            val typing = valueExprTyping(it.expr)
            typing.copy(monoEnv = typing.monoEnv + (it.binding to typing.res))
        }

        val bodyTyping = valueExprTyping(expr.expr)

        val typing = combineTypings(bodyTyping.res, bindingTypings + bodyTyping)
        return typing.copy(monoEnv = typing.monoEnv - expr.bindings.map(Binding::binding))
    }

    private fun recurExprTyping(expr: RecurExpr): Typing {
        val exprTypings = expr.exprs.map { it.binding to valueExprTyping(it.expr) }

        return combineTypings(
            TypeVar("_recur"),
            exprTypings.map { it.second },
            monoEnv = exprTypings.associate { it.first to it.second.res })
    }

    fun valueExprTyping(expr: ValueExpr): Typing = when (expr) {
        is IntExpr -> primitiveTyping(IntType)
        is BoolExpr -> primitiveTyping(BoolType)
        is StringExpr -> primitiveTyping(StringType)
        is DoExpr -> doTyping(expr)
        is IfExpr -> ifTyping(expr)
        is VectorExpr -> collTyping(::VectorType, expr.exprs)
        is SetExpr -> collTyping(::SetType, expr.exprs)
        is FnExpr -> fnExprTyping(expr)
        is LocalVarExpr -> localVarExprTyping(expr.localVar)
        is GlobalVarExpr -> globalVarExprTyping(expr)
        is LetExpr -> letExprTyping(expr)
        is CallExpr -> callExprTyping(expr)
        is WithFxExpr -> withFxTyping(expr)
        is LoopExpr -> loopExprTyping(expr)
        is RecurExpr -> recurExprTyping(expr)
        else -> TODO()
    }
}

internal fun valueExprTyping(expr: ValueExpr) = TypeChecker().valueExprTyping(expr)