package brj

import brj.runtime.Symbol
import com.oracle.truffle.api.interop.TruffleObject

data class Typing(
    val res: MonoType,
    val monoEnv: Map<LocalVar, MonoType> = emptyMap(),
    val fx: Set<Symbol> = emptySet(),
) : TruffleObject {
    override fun toString() = "(Typing $monoEnv, $fx => $res)"
}

private data class Constraint(val leftType: MonoType, val rightType: MonoType)

private fun zipConstraints(lefts: List<MonoType>, rights: List<MonoType>): Set<Constraint> =
    lefts.zip(rights).map { Constraint(it.first, it.second) }.toSet()

private typealias Mapping = Map<TypeVar, MonoType>

private fun MonoType.apply(mapping: Mapping) =
    postWalk { if (this is TypeVar) mapping.getOrDefault(this, this) else this }

private fun Typing.instantiate(): Typing {
    val tvMapping: MutableMap<TypeVar, TypeVar> = mutableMapOf()

    fun MonoType.instantiate(): MonoType = postWalk {
        if (this is TypeVar) tvMapping.getOrElse(this) { TypeVar(s) } else this
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

/*
previously we returned the monoenv from this one - that's why it's a work queue rather than just side-effecting
well, that and trying not to blow the stack, but maybe that isn't an issue here.

1. do we need the monoenv? can we still preserve the compositional nature of the type-system without it?
2. if we do, can we still create it?

think the monoenv becomes a record of the bounds imposed by each sub-expr, which we could potentially then combine.
 */

/*
ok, so, doing it immutably, what's it look like?

let's say we keep Typing roughly the same: a return type, and a set of LocalVar assumptions

how do we combine them?

in the Erdi composition paper, they assign a new type variable to each LV, then apply the resultant mapping to both the
return type and the mono-env.
does this make sense when you're subtyping?
you have to pick a constraint order - which one?
given it's a local var, which is negative, we have to say that each of the resultant types are the lower bound of the new type var
i.e. the new type var can be passed as each of the composed types.
we don't want all the same union behaviour as these papers - we don't want Int | String, for example.
that kind of implies keeping the type vars with the types themselves - i.e. within the record type.
how about (fn [x] (if (:named? x) (:foo x) x))?
  (:foo x) is typed x:m->a where m = {:foo a & r1}, x is typed x:b->b,
  let's say the user then wrapped it up:
    (if p (:Foo. (:foo x)) (:X. x)) :: {x {:foo a & r1}} => (+ (:Foo a) (:X {:foo a & r1}))
    then wrap it in a case:

  fails an occurs check?
how about (fn [p x y] (if p x y))? - Bool -> a -> a -> a

it's only record/variant types that have bounds -
or maybe number types, if we want to allow widening
or maybe types from other languages.
so we should probably support arbitrary subtyping?

we keep the bounds separately til later because it could be that the type is used in different places,
only _some_ of which are then weakened - we want to keep the strong variants as late as possible.

 we have different hierarchies of types - records, variants, java classes, 'objects'

 {(def (.foo a b)
    ...)
  (def .bar 42)}

 generally values can't pass between hierarchies, but maybe java classes and objects are the exception to the rule?
 maybe (and I'm sure I've heard this before somewhere) 'everything is objects' - at least, they are at the Graal/Truffle level
 so this is a language that's designed just to reflect Truffle interop?
 maybe not such a stupid idea...

 */

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
                is RecordType -> if (c.rightType is RecordType) {
                    // TODO this assumes leftType <= rightType,
                    // but the rest of the codebase doesn't yet respect this consistently
                    c.leftType.entryTypes.forEach { (k, tLeft) ->
                        val tRight = c.rightType[k] ?: TODO("missing key: $k")
                        constraintQueue.add(Constraint(tLeft, tRight))
                    }

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

    private fun recordTyping(expr: RecordExpr): Typing {
        val typings = expr.entries.mapValues { valueExprTyping(it.value) }
        return combineTypings(RecordType(typings.mapValues { it.value.res }), typings.values)
    }

    private fun keywordTyping(expr: KeywordExpr): Typing {
        val res = TypeVar("res")
        return Typing(FnType(listOf(RecordType(mapOf(expr.key.key to res))), res))
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
        val paramTypings = expr.args.map(::valueExprTyping)

        val fnType = when (fnTyping.res) {
            is FnType -> fnTyping.res
            is TypeVar -> FnType((paramTypings.indices).map { TypeVar("p${it}") }, TypeVar("r"))
            else -> TODO()
        }

        if (paramTypings.size != fnType.paramTypes.size) TODO("arity mismatch: ${paramTypings.size} vs ${fnType.paramTypes.size}")

        return combineTypings(
            fnType.resType,
            paramTypings + fnTyping,
            zipConstraints(fnType.paramTypes, paramTypings.map { it.res }) + Constraint(fnTyping.res, fnType)
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
        is NilExpr -> TODO("nil typing")
        is IntExpr -> primitiveTyping(IntType)
        is BoolExpr -> primitiveTyping(BoolType)
        is StringExpr -> primitiveTyping(StringType)
        is DoExpr -> doTyping(expr)
        is IfExpr -> ifTyping(expr)
        is VectorExpr -> collTyping(::VectorType, expr.exprs)
        is SetExpr -> collTyping(::SetType, expr.exprs)
        is RecordExpr -> recordTyping(expr)
        is KeywordExpr -> keywordTyping(expr)
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
