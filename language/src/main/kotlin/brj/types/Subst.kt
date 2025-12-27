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
    this is VectorType && other is VectorType -> VectorType(el.join(other.el))
    else -> throw TypeErrorException("Cannot join $this with $other")
}

// Meet two base types - must be same or error
internal infix fun BaseType.meet(other: BaseType): BaseType = when {
    this == other -> this
    this is VectorType && other is VectorType -> VectorType(el.meet(other.el))
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
    is VectorType -> VectorType(el.applySubst(subst))
    else -> this
}

internal fun Type.applySubst(subst: Subst): Type {
    val bound = subst[tv] ?: return Type(nullability, tv, base?.applySubst(subst))
    return Type(bound.nullability, tv, base?.applySubst(subst) ?: bound.base)
}

class TypeErrorException(message: String) : Exception(message)
