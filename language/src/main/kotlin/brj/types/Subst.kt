package brj.types

import brj.types.Nullability.*

typealias Subst = Map<TypeVar, Type>

internal fun Subst.plusLower(tv: TypeVar, type: Type): Subst =
    this + (tv to (this[tv]?.join(type) ?: type))

internal fun Subst.plusUpper(tv: TypeVar, type: Type): Subst =
    this + (tv to (this[tv]?.meet(type) ?: type))

// Nullability join: more nullable wins (for outputs/if-branches)
internal infix fun Nullability.join(other: Nullability): Nullability = when {
    this == NULLABLE || other == NULLABLE -> NULLABLE
    this == MAYBE_NULL || other == MAYBE_NULL -> MAYBE_NULL
    else -> NOT_NULL
}

// Nullability meet: more restrictive wins (for inputs/requirements)
internal infix fun Nullability.meet(other: Nullability): Nullability = when {
    this == NOT_NULL || other == NOT_NULL -> NOT_NULL
    this == MAYBE_NULL || other == MAYBE_NULL -> MAYBE_NULL
    else -> NULLABLE
}

// Join two base types - must be same or error
internal infix fun BaseType.join(other: BaseType): BaseType = when {
    this == other -> this
    this is AppliedType && other is AppliedType && ctor == other.ctor ->
        AppliedType(ctor, ctor.variances.zip(args.zip(other.args)).map { (variance, pair) ->
            val (a, b) = pair
            when (variance) {
                Variance.OUT -> a.join(b)
                Variance.IN -> a.meet(b)
                Variance.INVARIANT -> if (a == b) a else throw TypeErrorException("Cannot join invariant type args: $a vs $b")
            }
        })
    this is FnType && other is FnType -> {
        val (shorter, longer) = if (paramTypes.size <= other.paramTypes.size) this to other else other to this
        if (shorter.paramTypes.size != longer.paramTypes.size) {
            if (longer.paramTypes.size != shorter.paramTypes.size + 1 || longer.paramTypes.last().base !is RecordType)
                throw TypeErrorException("Cannot join functions with different arities")
        }
        // Join uses the shorter param list (common ground)
        FnType(shorter.paramTypes.zip(longer.paramTypes) { a, b -> a.meet(b) }, shorter.returnType.join(longer.returnType))
    }
    else -> throw TypeErrorException("Cannot join $this with $other")
}

// Meet two base types - must be same or error
internal infix fun BaseType.meet(other: BaseType): BaseType = when {
    this == other -> this
    this is AppliedType && other is AppliedType && ctor == other.ctor ->
        AppliedType(ctor, ctor.variances.zip(args.zip(other.args)).map { (variance, pair) ->
            val (a, b) = pair
            when (variance) {
                Variance.OUT -> a.meet(b)
                Variance.IN -> a.join(b)
                Variance.INVARIANT -> if (a == b) a else throw TypeErrorException("Cannot meet invariant type args: $a vs $b")
            }
        })
    this is FnType && other is FnType -> {
        val (shorter, longer) = if (paramTypes.size <= other.paramTypes.size) this to other else other to this
        if (shorter.paramTypes.size != longer.paramTypes.size) {
            if (longer.paramTypes.size != shorter.paramTypes.size + 1 || longer.paramTypes.last().base !is RecordType)
                throw TypeErrorException("Cannot meet functions with different arities")
        }
        // Meet uses the longer param list (more specific)
        val commonParams = shorter.paramTypes.zip(longer.paramTypes) { a, b -> a.join(b) }
        val extraParams = longer.paramTypes.drop(shorter.paramTypes.size)
        FnType(commonParams + extraParams, shorter.returnType.meet(longer.returnType))
    }
    else -> throw TypeErrorException("Cannot meet $this with $other")
}

// Join two types (for if-branches, collecting outputs)
internal infix fun Type.join(other: Type): Type {
    val newBase = if (base != null && other.base != null) base.join(other.base) else base ?: other.base
    val newNull = nullability.join(other.nullability)
    return Type(newNull, TypeVar(), newBase)
}

// Meet two types (for requirements, inputs)
internal infix fun Type.meet(other: Type): Type {
    val newBase = if (base != null && other.base != null) base.meet(other.base) else base ?: other.base
    val newNull = nullability.meet(other.nullability)
    return Type(newNull, TypeVar(), newBase)
}

internal fun BaseType.applySubst(subst: Subst): BaseType = when (this) {
    is AppliedType -> AppliedType(ctor, args.map { it.applySubst(subst) })
    is FnType -> FnType(paramTypes.map { it.applySubst(subst) }, returnType.applySubst(subst))
    else -> this
}

internal fun Type.applySubst(subst: Subst): Type {
    val rawBound = subst[tv] ?: return Type(nullability, tv, base?.applySubst(subst))
    val bound = if (rawBound.tv != tv) rawBound.applySubst(subst) else rawBound
    return Type(bound.nullability, tv, base?.applySubst(subst) ?: bound.base)
}

class TypeErrorException(message: String) : Exception(message)
