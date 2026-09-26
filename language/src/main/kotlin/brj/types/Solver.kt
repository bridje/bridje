package brj.types


private const val J_L_ITERABLE = "java.lang.Iterable"
private const val J_U_ITERATOR = "java.util.Iterator"

// Subtype constraints in the simple-sub style over an immutable bound environment: a bound added to a
// variable is checked against every bound already on the other side, and the in-flight cache is what
// makes cycles terminate. `constrain` is a function from one environment to the next.
fun BoundEnv.constrain(lower: Type, upper: Type, ctx: TypeCtx = TypeCtx.EMPTY): BoundEnv =
    Solve(this, ctx).also { it.check(lower, upper) }.env

fun BoundEnv.constrainAll(constraints: Iterable<Pair<Type, Type>>, ctx: TypeCtx = TypeCtx.EMPTY): BoundEnv =
    Solve(this, ctx).also { s -> constraints.forEach { (l, u) -> s.check(l, u) } }.env

// base ∧ filter in normal form. A meet survives only on a variable, or on a nominal the filter adds to.
internal fun meetOf(base: Type, filter: Filter, ctx: TypeCtx): Type {
    if (filter.isIdentity) return base
    return when (base) {
        is Meet -> meetOf(base.base, base.filter and filter, ctx)
        is TypeVar -> Meet(base, filter)
        is RecordType -> if (filter.record) RecordType(base.keys + filter.keys) else base
        is TagType, is EnumType -> {
            if (!filter.record) return base
            val keys = ctx.recordKeys(base) ?: fail("$base is not a record")
            if (keys.containsAll(filter.keys)) base else Meet(base, filter)
        }
        is NullableType -> if (filter.record) fail("$base may be nil, and with needs a record") else meetOf(base.inner, filter, ctx)
        NothingType -> NothingType
        is PrimType, is FnType, is VectorType, is SetType, is HostType, is IterableType, is IteratorType ->
            if (filter.record) fail("$base is not a record") else base
    }
}

// A function taking a trailing record may be called without it: the options-record convention.
private fun commonParamCount(lower: FnType, upper: FnType, isRecord: (Type) -> Boolean): Int? {
    val l = lower.paramTypes
    val u = upper.paramTypes
    return when {
        l.size == u.size -> l.size
        l.size == u.size + 1 && isRecord(l.last()) -> u.size
        u.size == l.size + 1 && isRecord(u.last()) -> l.size
        else -> null
    }
}

private fun fail(message: String): Nothing = throw TypeCheckException(message)

private fun isNullish(t: Type) = t is NullableType

private fun split(t: Type): Pair<Type, Filter> = if (t is Meet) t.base to t.filter else t to Filter()

// The accumulator behind one `constrain` call. It starts from an environment and ends as a new one;
// nothing outside it observes the intermediate states.
private class Solve(start: BoundEnv, private val ctx: TypeCtx) {
    private val bounds = HashMap(start)
    private val cache = HashSet<Pair<Type, Type>>()

    val env: BoundEnv get() = bounds.toMap()

    // A failure deep in a decomposition names the pair that failed; the constraint it was serving is the
    // reader's provenance, so it is carried up with it.
    fun check(lower: Type, upper: Type) {
        try {
            constrain(lower, upper)
        } catch (e: TypeCheckException) {
            if (e.provenance == null) throw TypeCheckException(e.message!!, lower to upper)
            throw e
        }
    }

    private fun isRecord(t: Type): Boolean = when (t) {
        is RecordType -> true
        is Meet -> t.filter.record
        is TypeVar -> upperOf(t).concrete is RecordType
        else -> false
    }

    private fun lowerOf(tv: TypeVar) = bounds[tv]?.lower ?: LowerBound()
    private fun upperOf(tv: TypeVar) = bounds[tv]?.upper ?: UpperBound()
    private fun setLower(tv: TypeVar, b: LowerBound) { bounds[tv] = Bounds(b, upperOf(tv)) }
    private fun setUpper(tv: TypeVar, b: UpperBound) { bounds[tv] = Bounds(lowerOf(tv), b) }

    fun constrain(lower: Type, upper: Type) {
        if (lower === upper) return
        if (!cache.add(lower to upper)) return
        if (Thread.currentThread().isInterrupted) fail("type checking interrupted")

        when {
            lower is NothingType -> {}

            // L ≤ base ∧ F iff L ≤ base and L passes F. Meets never enter an upper bound.
            upper is Meet -> {
                constrain(lower, upper.base)
                when {
                    upper.filter.record -> constrain(lower, RecordType(upper.filter.keys))
                    upper.filter.notNil && isNullish(lower) -> fail("$lower is nullable, but must not be")
                }
            }

            // An edge or bound already recorded has already been propagated: nothing new to do.
            lower is TypeVar && upper is TypeVar -> {
                if (upper in upperOf(lower).tvs) return
                setUpper(lower, upperOf(lower).let { it.copy(tvs = it.tvs + upper) })
                addLower(upper, lower)
                lowerOf(lower).asTypes().forEach { constrain(it, upper) }
            }

            lower is TypeVar -> {
                val before = upperOf(lower)
                addUpper(lower, upper)
                if (upperOf(lower) == before) return
                lowerOf(lower).asTypes().forEach { constrain(it, upper) }
            }

            upper is TypeVar -> {
                val before = lowerOf(upper)
                val added = addLower(upper, lower) ?: return
                if (lowerOf(upper) == before) return
                upperOf(upper).asTypes().forEach { constrain(added, it) }
            }

            upper is NullableType -> when (lower) {
                is NullableType -> constrain(lower.inner, upper.inner)
                else -> constrain(lower, upper.inner)
            }

            lower == NothingType.nullable() -> fail("nil is not a $upper, which is not nullable")
            lower is NullableType -> fail("$lower may be nil, and $upper is not nullable")

            lower is Meet -> constrainMeet(lower, upper)

            lower is PrimType && upper is PrimType -> if (lower != upper) fail("$lower is not a subtype of $upper")

            lower is FnType && upper is FnType -> {
                val n = commonParamCount(lower, upper, ::isRecord)
                    ?: fail("function arity mismatch: ${lower.paramTypes.size} vs ${upper.paramTypes.size}")
                (0 until n).forEach { i -> constrain(upper.paramTypes[i], lower.paramTypes[i]) }
                constrain(lower.returnType, upper.returnType)
            }

            lower is VectorType && upper is VectorType -> constrain(lower.el, upper.el)
            lower is SetType && upper is SetType -> constrain(lower.el, upper.el)

            lower is HostType && upper is HostType -> constrainHost(lower, upper)

            // Protocol types are covariant in the element: they are read through.
            lower is IterableType && upper is IterableType -> constrain(lower.el, upper.el)
            lower is IteratorType && upper is IteratorType -> constrain(lower.el, upper.el)
            lower is VectorType && upper is IterableType -> constrain(lower.el, upper.el)
            lower is SetType && upper is IterableType -> constrain(lower.el, upper.el)
            lower is HostType && upper is IterableType -> constrainHostProtocol(lower, J_L_ITERABLE, upper.el)
            lower is HostType && upper is IteratorType -> constrainHostProtocol(lower, J_U_ITERATOR, upper.el)

            // Width subtyping: the demanded keys must all be present.
            lower is RecordType && upper is RecordType -> requireKeys(lower, lower.keys, upper)

            // Tag arguments are covariant: the payload is immutable.
            lower is TagType && upper is TagType -> {
                if (lower.tag != upper.tag) fail("$lower is not a subtype of $upper")
                constrainArgs(lower.args, upper.args)
            }
            lower is TagType && upper is EnumType -> {
                if (ctx.tagInfo(lower.tag).enum != upper.enum) fail("$lower is not a subtype of $upper")
                constrainArgs(lower.args, upper.args)
            }
            lower is EnumType && upper is EnumType -> {
                if (lower.enum != upper.enum) fail("$lower is not a subtype of $upper")
                constrainArgs(lower.args, upper.args)
            }

            (lower is TagType || lower is EnumType) && upper is RecordType ->
                requireKeys(lower, ctx.recordKeys(lower) ?: fail("$lower is not a record"), upper)

            else -> fail("$lower is not a subtype of $upper")
        }
    }

    private fun constrainArgs(lower: List<Type>, upper: List<Type>) {
        if (lower.size != upper.size) fail("type argument count mismatch: $lower vs $upper")
        lower.zip(upper).forEach { (l, u) -> constrain(l, u) }
    }

    // Host arguments are invariant. Across classes the subclass's arguments are carried up the hierarchy;
    // an erased side is compatible with anything.
    private fun constrainHost(lower: HostType, upper: HostType) {
        val mapped = if (lower.className == upper.className) lower.args
        else HostTypeHierarchy.mapSupertypeArgs(lower.className, upper.className, lower.args) { TypeVar() }
            ?: fail("$lower is not a subtype of $upper")
        if (mapped.isNotEmpty() && upper.args.isNotEmpty()) {
            if (mapped.size != upper.args.size) fail("type argument count mismatch: $lower vs $upper")
            mapped.zip(upper.args).forEach { (l, u) -> invariant(l, u) }
        }
    }

    // A host class reaches a protocol type through the Java interface behind it, covariantly.
    private fun constrainHostProtocol(lower: HostType, iface: String, el: Type) {
        val mapped = HostTypeHierarchy.mapSupertypeArgs(lower.className, iface, lower.args) { TypeVar() }
            ?: fail("$lower is not a subtype of ${iface.substringAfterLast('.')}")
        mapped.singleOrNull()?.let { constrain(it, el) }
    }

    private fun invariant(a: Type, b: Type) {
        constrain(a, b)
        constrain(b, a)
    }

    private fun requireKeys(lower: Type, present: Set<brj.runtime.QSymbol>, upper: RecordType) {
        val missing = upper.keys - present
        if (missing.isNotEmpty()) fail("$lower lacks ${RecordType(missing)}")
    }

    // base ∧ F ≤ U: the filter supplies part of the demand, the base owes the rest.
    private fun constrainMeet(lower: Meet, upper: Type) {
        val (base, f) = lower.base to lower.filter
        when {
            upper is RecordType -> when {
                f.record -> (upper.keys - f.keys).let { rest -> if (rest.isNotEmpty()) constrain(base, RecordType(rest)) }
                else -> constrain(base, upper.nullable())
            }
            upper is NothingType -> constrain(base, if (f.notNil) NothingType.nullable() else NothingType)
            f.record && (upper is PrimType || upper is FnType || upper is VectorType || upper is SetType) ->
                fail("a record is not a $upper")
            f.record -> constrain(base, upper)
            else -> constrain(base, upper.nullable())
        }
    }

    // Adds a lower bound, returning the disjunct to check against the upper bounds: the merged concrete
    // type, the normalised meet, or null when the bound was absorbed or is a bare variable.
    private fun addLower(tv: TypeVar, t: Type): Type? {
        val b = lowerOf(tv)
        return when (t) {
            NothingType -> null
            is NullableType -> {
                setLower(tv, b.copy(nullable = true))
                addLower(tv, t.inner)
                lowerOf(tv).concreteType()
            }
            is TypeVar -> {
                // (α ∧ F) ∨ α is α.
                setLower(tv, b.copy(tvs = b.tvs + t, meets = b.meets - t))
                null
            }
            is Meet -> when (val base = t.base) {
                is TypeVar -> {
                    if (base in b.tvs) return null
                    if (t.filter.record && b.concrete != null && ctx.recordKeys(b.concrete) == null)
                        fail("Cannot join ${b.concrete} with $t")
                    val f = b.meets[base]?.let { it or t.filter } ?: t.filter
                    if (f.isIdentity) {
                        setLower(tv, b.copy(tvs = b.tvs + base, meets = b.meets - base))
                        null
                    } else {
                        setLower(tv, b.copy(meets = b.meets + (base to f)))
                        Meet(base, f)
                    }
                }
                else -> addConcrete(tv, t)
            }
            else -> addConcrete(tv, t)
        }
    }

    private fun addConcrete(tv: TypeVar, t: Type): Type? {
        val b = lowerOf(tv)
        if (b.meets.values.any { it.record } && ctx.recordKeys(t) == null) fail("Cannot join a record with $t")
        val merged = b.concrete?.let { join(it, t) } ?: t
        setLower(tv, b.copy(concrete = merged))
        return lowerOf(tv).concreteType()
    }

    private fun addUpper(tv: TypeVar, t: Type) {
        val b = upperOf(tv)
        when (t) {
            NothingType -> setUpper(tv, b.copy(concrete = NothingType, nilOk = false))
            is NullableType -> addNonNullUpper(tv, t.inner, nilOk = true)
            is TypeVar -> setUpper(tv, b.copy(tvs = b.tvs + t))
            else -> addNonNullUpper(tv, t, nilOk = false)
        }
    }

    private fun addNonNullUpper(tv: TypeVar, t: Type, nilOk: Boolean) {
        val b = upperOf(tv).let { if (nilOk) it else it.copy(nilOk = false) }
        when (t) {
            // α ≤ β? : recorded as α ≤ β, which forbids nil flowing through and is therefore
            // stricter than the truth. Loosened when declared types with nullable variables land.
            is TypeVar -> setUpper(tv, b.copy(tvs = b.tvs + t))
            else -> setUpper(tv, b.copy(concrete = b.concrete?.let { meet(it, t) } ?: t))
        }
    }

    // Least upper bound of two concrete, non-nullable types of the same kind.
    // Structured types merge component-wise through a fresh variable, so the result stays a single bound.
    private fun join(a: Type, b: Type): Type = when {
        a == b -> a
        a is NothingType -> b
        b is NothingType -> a
        a is FnType && b is FnType -> {
            if (a.paramTypes.size != b.paramTypes.size) fail("Cannot join $a with $b: arity differs")
            FnType(
                a.paramTypes.zip(b.paramTypes).map { (pa, pb) -> meetVia(pa, pb) },
                joinVia(a.returnType, b.returnType),
            )
        }
        a is VectorType && b is VectorType -> VectorType(joinVia(a.el, b.el))
        a is SetType && b is SetType -> SetType(joinVia(a.el, b.el))
        a is IterableType && b is IterableType -> IterableType(joinVia(a.el, b.el))
        a is IteratorType && b is IteratorType -> IteratorType(joinVia(a.el, b.el))
        a is VectorType && b is IterableType -> IterableType(joinVia(a.el, b.el))
        a is IterableType && b is VectorType -> IterableType(joinVia(a.el, b.el))
        a is SetType && b is IterableType -> IterableType(joinVia(a.el, b.el))
        a is IterableType && b is SetType -> IterableType(joinVia(a.el, b.el))
        a is HostType && b is HostType -> hostJoin(a, b)
        a is RecordType && b is RecordType -> RecordType(a.keys intersect b.keys)
        else -> {
            val (ba, fa) = split(a)
            val (bb, fb) = split(b)
            when {
                // Two nominals join to their least common ancestor, keeping what both filters promise.
                ctx.isNominal(ba) && ctx.isNominal(bb) -> meetOf(nominalJoin(ba, bb), fa or fb, ctx)
                // A record and a record-shaped nominal join structurally.
                else -> {
                    val ka = ctx.recordKeys(a)
                    val kb = ctx.recordKeys(b)
                    if (ka != null && kb != null) RecordType(ka intersect kb) else fail("Cannot join $a with $b")
                }
            }
        }
    }

    // Host classes join only along the class chain: there is no declared least upper bound otherwise.
    private fun hostJoin(a: HostType, b: HostType): Type {
        if (a.className == b.className) return hostWithArgs(a.className, a.args, b.args) { x, y -> invVia(x, y) }
        HostTypeHierarchy.mapSupertypeArgs(a.className, b.className, a.args) { TypeVar() }
            ?.let { return hostWithArgs(b.className, it, b.args) { x, y -> invVia(x, y) } }
        HostTypeHierarchy.mapSupertypeArgs(b.className, a.className, b.args) { TypeVar() }
            ?.let { return hostWithArgs(a.className, it, a.args) { x, y -> invVia(x, y) } }
        fail("Cannot join $a with $b: no common superclass is declared")
    }

    private fun hostWithArgs(className: String, a: List<Type>, b: List<Type>, merge: (Type, Type) -> Type): HostType = when {
        a.isEmpty() || b.isEmpty() -> HostType(className)
        a.size != b.size -> fail("type argument count mismatch on $className")
        else -> HostType(className, a.zip(b).map { (x, y) -> merge(x, y) })
    }

    private fun nominalJoin(a: Type, b: Type): Type = when {
        a is TagType && b is TagType && a.tag == b.tag -> TagType(a.tag, joinArgs(a.args, b.args))
        a is TagType && b is TagType -> {
            val ea = ctx.tagInfo(a.tag).enum
            if (ea != null && ea == ctx.tagInfo(b.tag).enum) EnumType(ea, joinArgs(a.args, b.args))
            else fail("Cannot join $a with $b")
        }
        a is TagType && b is EnumType -> nominalJoin(b, a)
        a is EnumType && b is TagType ->
            if (ctx.tagInfo(b.tag).enum == a.enum) EnumType(a.enum, joinArgs(a.args, b.args)) else fail("Cannot join $a with $b")
        a is EnumType && b is EnumType && a.enum == b.enum -> EnumType(a.enum, joinArgs(a.args, b.args))
        else -> fail("Cannot join $a with $b")
    }

    private fun joinArgs(a: List<Type>, b: List<Type>): List<Type> {
        if (a.size != b.size) fail("type argument count mismatch: $a vs $b")
        return a.zip(b).map { (x, y) -> joinVia(x, y) }
    }

    // Greatest lower bound of two concrete, non-nullable types. Across kinds it is Nothing:
    // nothing can satisfy both, and the error surfaces when something tries to.
    private fun meet(a: Type, b: Type): Type = when {
        a == b -> a
        a is NothingType || b is NothingType -> NothingType
        a is FnType && b is FnType -> {
            if (a.paramTypes.size != b.paramTypes.size) NothingType
            else FnType(
                a.paramTypes.zip(b.paramTypes).map { (pa, pb) -> joinVia(pa, pb) },
                meetVia(a.returnType, b.returnType),
            )
        }
        a is VectorType && b is VectorType -> VectorType(meetVia(a.el, b.el))
        a is SetType && b is SetType -> SetType(meetVia(a.el, b.el))
        a is IterableType && b is IterableType -> IterableType(meetVia(a.el, b.el))
        a is IteratorType && b is IteratorType -> IteratorType(meetVia(a.el, b.el))
        a is VectorType && b is IterableType -> VectorType(meetVia(a.el, b.el))
        a is IterableType && b is VectorType -> VectorType(meetVia(a.el, b.el))
        a is SetType && b is IterableType -> SetType(meetVia(a.el, b.el))
        a is IterableType && b is SetType -> SetType(meetVia(a.el, b.el))
        a is HostType && (b is IterableType || b is IteratorType) -> a
        (a is IterableType || a is IteratorType) && b is HostType -> b
        a is HostType && b is HostType -> hostMeet(a, b)
        a is RecordType && b is RecordType -> RecordType(a.keys + b.keys)
        else -> {
            val (ba, fa) = split(a)
            val (bb, fb) = split(b)
            when {
                ctx.isNominal(ba) && ctx.isNominal(bb) -> nominalMeet(ba, bb)?.let { meetOf(it, fa and fb, ctx) } ?: NothingType
                // A nominal demanded to carry extra keys: the promotion User ∧ {email}.
                ctx.isNominal(ba) && bb is RecordType && ctx.recordKeys(ba) != null -> meetOf(ba, fa and Filter.keys(bb.keys), ctx)
                ba is RecordType && ctx.isNominal(bb) && ctx.recordKeys(bb) != null -> meetOf(bb, fb and Filter.keys(ba.keys), ctx)
                else -> NothingType
            }
        }
    }

    private fun hostMeet(a: HostType, b: HostType): Type {
        if (a.className == b.className) return hostWithArgs(a.className, a.args, b.args) { x, y -> invVia(x, y) }
        HostTypeHierarchy.mapSupertypeArgs(a.className, b.className, a.args) { TypeVar() }
            ?.let { mapped -> mapped.zip(b.args).forEach { (x, y) -> invVia(x, y) }; return a }
        HostTypeHierarchy.mapSupertypeArgs(b.className, a.className, b.args) { TypeVar() }
            ?.let { mapped -> mapped.zip(a.args).forEach { (x, y) -> invVia(x, y) }; return b }
        return NothingType
    }

    private fun nominalMeet(a: Type, b: Type): Type? = when {
        a is TagType && b is TagType -> if (a.tag == b.tag) TagType(a.tag, meetArgs(a.args, b.args)) else null
        a is TagType && b is EnumType -> if (ctx.tagInfo(a.tag).enum == b.enum) TagType(a.tag, meetArgs(a.args, b.args)) else null
        a is EnumType && b is TagType -> nominalMeet(b, a)
        a is EnumType && b is EnumType -> if (a.enum == b.enum) EnumType(a.enum, meetArgs(a.args, b.args)) else null
        else -> null
    }

    private fun meetArgs(a: List<Type>, b: List<Type>): List<Type> {
        if (a.size != b.size) fail("type argument count mismatch: $a vs $b")
        return a.zip(b).map { (x, y) -> meetVia(x, y) }
    }

    // A join or meet of two types is a fresh variable bounded by both, unless one side is a variable
    // already known to bound the other: then it is that variable, so a bound flowing round a cycle
    // settles instead of minting a variable on every pass.
    private fun joinVia(a: Type, b: Type): Type = when {
        a == b -> a
        b is TypeVar && isBelow(a, b) -> b
        a is TypeVar && isBelow(b, a) -> a
        else -> TypeVar().also { constrain(a, it); constrain(b, it) }
    }

    private fun meetVia(a: Type, b: Type): Type = when {
        a == b -> a
        b is TypeVar && isAbove(a, b) -> b
        a is TypeVar && isAbove(b, a) -> a
        else -> TypeVar().also { constrain(it, a); constrain(it, b) }
    }

    private fun isBelow(t: Type, v: TypeVar): Boolean =
        lowerOf(v).let { lo -> if (t is TypeVar) t in lo.tvs else lo.concrete == t || lo.concreteType() == t }

    private fun isAbove(t: Type, v: TypeVar): Boolean =
        upperOf(v).let { up -> if (t is TypeVar) t in up.tvs else up.concrete == t || up.concreteType() == t }

    private fun invVia(a: Type, b: Type): Type =
        if (a == b) a else TypeVar().also { invariant(a, it); invariant(b, it) }
}
