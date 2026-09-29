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

        // A rigid variable, from a declaration a nested value was checked against, stays rigid in every copy.
        val renaming = reachable.associateWith { TypeVar(rigid = it.rigid) }
        fun rename(tv: TypeVar) = renaming[tv] ?: tv
        fun rename(t: Type): Type = t.mapVars(::rename)

        val copied = reachable.mapNotNull { tv ->
            bounds[tv]?.let { b ->
                renaming.getValue(tv) to Bounds(
                    LowerBound(
                        concrete = rename(b.lower.concrete),
                        tvs = b.lower.tvs.map(::rename).toSet(),
                        meets = b.lower.meets.entries.associate { (k, keys) -> rename(k) to keys.mapSlotArgs(::rename) },
                    ),
                    UpperBound(
                        concrete = b.upper.concrete?.let(::rename),
                        tvs = b.upper.tvs.entries.associate { (k, nilOk) -> rename(k) to nilOk },
                    ),
                )
            }
        }.toMap()

        return rename(type) to copied
    }
}

internal fun Type.mapVars(f: (TypeVar) -> TypeVar): Type = when (val b = base) {
    is TypeVar -> copy(base = f(b))
    is Base.OnVar -> copy(base = Base.OnVar(b.keys.mapSlotArgs { it.mapVars(f) }, f(b.base)))
    else -> copy(base = b.mapTypes { it.mapVars(f) })
}

// A record on a variable substituted by anything but a variable keeps its variable: what it stands for is
// the solver's to say, through `withKeys`, not a substitution's.
internal fun Type.substitute(s: Map<TypeVar, Type>): Type = when (val b = base) {
    is TypeVar -> s[b]?.let { if (nullable) it.nullable() else it } ?: this
    is Base.OnVar -> {
        val keys = b.keys.mapSlotArgs { it.substitute(s) }
        copy(base = Base.OnVar(keys, s[b.base]?.bareVar ?: b.base))
    }
    else -> copy(base = b.mapTypes { it.substitute(s) })
}

// The scheme of a top-level definition's body. A free local here is an analyser bug, not a type error.
internal fun Typing.generalise(): Scheme {
    check(monoEnv.isEmpty()) { "cannot generalise a typing with free locals: ${monoEnv.keys}" }
    return Scheme(type, bounds)
}

// [actual] below [declared] at every instance of it: the declared type's variables are held rigid, so the
// solver rejects any bound that reaches one, and [error] says what was more general than what.
internal fun BoundEnv.constrainRigidly(actual: Type, declared: Type, ctx: TypeCtx, error: () -> String): BoundEnv {
    val rigid = declared.typeVars().associateWith { TypeVar(rigid = true) }
    try {
        return constrain(actual, declared.mapVars { rigid[it] ?: it }, ctx)
    } catch (_: RigidBoundException) {
        throw TypeCheckException(error())
    }
}

// The inferred scheme must be at least as general as the declaration, so the declared type is the exported
// one, and an annotation may narrow but never widen (D29 on #129).
fun checkDeclared(inferred: Scheme, declared: Type, ctx: TypeCtx): Scheme {
    val (type, bounds) = inferred.instantiate()
    bounds.constrainRigidly(type, declared, ctx) {
        "declared type ${Scheme(declared).simplify(ctx)} is more general than the definition, whose type is ${inferred.simplify(ctx)}"
    }
    return Scheme(declared)
}
