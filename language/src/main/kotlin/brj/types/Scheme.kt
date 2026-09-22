package brj.types

// A generalised typing: the type of a top-level definition together with the bounds of every
// variable it mentions. Bridje generalises only at the top level, so there is no outer scope to
// exclude and every variable in the scheme is quantified.
data class Scheme(val type: Type, val bounds: BoundEnv = emptyMap()) {

    // A fresh copy of the reachable part of the scheme: the type with every variable renamed, and the
    // renamed bounds to merge into the caller's environment.
    fun instantiate(): Pair<Type, BoundEnv> {
        val reachable = LinkedHashSet<TypeVar>()
        val pending = ArrayDeque<Type>()
        pending += type
        while (pending.isNotEmpty()) {
            for (tv in pending.removeFirst().typeVars()) {
                if (reachable.add(tv)) {
                    bounds[tv]?.let { b ->
                        pending += b.lower.asTypes()
                        pending += b.upper.asTypes()
                    }
                }
            }
        }

        val renaming = reachable.associateWith { TypeVar() }
        fun rename(t: Type): Type = t.mapVars { renaming[it] ?: it }

        val copied = reachable.mapNotNull { tv ->
            bounds[tv]?.let { b ->
                renaming.getValue(tv) to Bounds(
                    LowerBound(
                        concrete = b.lower.concrete?.let(::rename),
                        nullable = b.lower.nullable,
                        tvs = b.lower.tvs.map { renaming[it] ?: it }.toSet(),
                        meets = b.lower.meets.entries.associate { (k, f) -> (renaming[k] ?: k) to f },
                    ),
                    UpperBound(
                        concrete = b.upper.concrete?.let(::rename),
                        nilOk = b.upper.nilOk,
                        tvs = b.upper.tvs.map { renaming[it] ?: it }.toSet(),
                    ),
                )
            }
        }.toMap()

        return rename(type) to copied
    }
}

internal fun Type.mapVars(f: (TypeVar) -> Type): Type = when (this) {
    is TypeVar -> f(this)
    is NullableType -> inner.mapVars(f).nullable()
    is FnType -> FnType(paramTypes.map { it.mapVars(f) }, returnType.mapVars(f))
    is VectorType -> VectorType(el.mapVars(f))
    is SetType -> SetType(el.mapVars(f))
    is IterableType -> IterableType(el.mapVars(f))
    is IteratorType -> IteratorType(el.mapVars(f))
    is HostType -> HostType(className, args.map { it.mapVars(f) })
    is TagType -> TagType(tag, args.map { it.mapVars(f) })
    is EnumType -> EnumType(enum, args.map { it.mapVars(f) })
    is Meet -> Meet(base.mapVars(f), filter)
    is PrimType, is RecordType, BottomType, NilType -> this
}

// The scheme of a top-level definition's body. A free local here is an analyser bug, not a type error.
internal fun Typing.generalise(): Scheme {
    check(monoEnv.isEmpty()) { "cannot generalise a typing with free locals: ${monoEnv.keys}" }
    return Scheme(type, bounds)
}

// The inferred scheme must be at least as general as the declaration: the definition is checked against
// the declared type with the declaration's variables held rigid. The declared type is then the exported
// one, so an annotation may narrow but never widen (D29 on #129).
fun checkDeclared(inferred: Scheme, declared: Type, ctx: TypeCtx): Scheme {
    val rigid = declared.typeVars()
    val (type, bounds) = inferred.instantiate()
    val env = bounds.constrain(type, declared, ctx)
    for (tv in rigid) {
        val lower = env.lower(tv)
        val upper = env.upper(tv)
        if (lower.concrete != null || lower.nullable || lower.meets.isNotEmpty() || upper.concrete != null)
            throw TypeCheckException("declared type $declared is more general than the definition, whose type is ${type.positive(env, ctx)}")
    }
    return Scheme(declared)
}
