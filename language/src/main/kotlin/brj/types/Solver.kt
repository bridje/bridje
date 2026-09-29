package brj.types

import brj.runtime.QSymbol


private const val J_L_ITERABLE = "java.lang.Iterable"
private const val J_U_ITERATOR = "java.util.Iterator"

// Subtype constraints in the simple-sub style over an immutable bound environment: a bound added to a
// variable is checked against every bound already on the other side, and the in-flight cache is what
// makes cycles terminate. `constrain` is a function from one environment to the next.
fun BoundEnv.constrain(lower: Type, upper: Type, ctx: TypeCtx = TypeCtx.EMPTY): BoundEnv =
    Solve(this, ctx).also { it.check(lower, upper) }.env

fun BoundEnv.constrainAll(constraints: Iterable<Pair<Type, Type>>, ctx: TypeCtx = TypeCtx.EMPTY): BoundEnv =
    Solve(this, ctx).also { s -> constraints.forEach { (l, u) -> s.check(l, u) } }.env

// A value of t with the keys given as well, each at its slot's instance: the type of `with`.
// - A named record keeps its name where the keys it declares keep their types. Giving one of them a new
//   instance would change the name's arguments, so the result is the record without its name.
// - An enum's key that only some variants declare is now present in all of them, so it stays in the keys.
internal fun withKeys(t: Type, keys: Map<QSymbol, Slot>, ctx: TypeCtx): Type {
    if (t.nullable) fail("$t may be nil, and with needs a record")
    return when (val b = t.base) {
        Base.Nothing -> t
        is TypeVar -> OnVarType(keys, b)
        is Base.OnVar -> OnVarType(b.keys + keys, b.base)
        is Base.Rec -> {
            if (!ctx.isRecord(b.name)) fail("$t is not a record")
            val declared = b.name?.let { ctx.declared(it, b.args) }.orEmpty()
            if (keys.keys.none { it in declared && !ctx.isMono(it) }) {
                Type(b.copy(keys = b.keys + keys.filterKeys { declared[it]?.required != true }))
            } else {
                val all = ctx.slotsOf(b).mapValues { (k, f) ->
                    Slot(f.required, f.instances.singleOrNull() ?: fail("with on $t cannot give ${k.toDisplayString()} one type"))
                }
                RecType(null, emptyList(), all + keys, b.closed)
            }
        }
        is Base.Prim, is Base.Fn, is Base.Vector, is Base.Set, is Base.Host, is Base.Iterable, is Base.Iterator ->
            fail("$t is not a record")
    }
}

// The record every record is below.
internal val AnyRecord = RecType(null, emptyList(), emptyMap())

private fun fail(message: String): Nothing = throw TypeCheckException(message)

// A bound reached a rigid variable: the declaration holding it is more general than the definition.
internal class RigidBoundException(message: String) : TypeCheckException(message)

private fun rigidFail(message: String): Nothing = throw RigidBoundException(message)

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
        } catch (e: RigidBoundException) {
            throw e
        } catch (e: TypeCheckException) {
            if (e.provenance == null) throw TypeCheckException(e.message!!, lower to upper)
            throw e
        }
    }

    private fun lowerOf(tv: TypeVar) = bounds[tv]?.lower ?: LowerBound()
    private fun upperOf(tv: TypeVar) = bounds[tv]?.upper ?: UpperBound()
    private fun setLower(tv: TypeVar, b: LowerBound) { bounds[tv] = Bounds(b, upperOf(tv)) }
    private fun setUpper(tv: TypeVar, b: UpperBound) { bounds[tv] = Bounds(lowerOf(tv), b) }

    fun constrain(lower: Type, upper: Type) {
        if (lower == upper) return
        if (!cache.add(lower to upper)) return
        if (Thread.currentThread().isInterrupted) fail("type checking interrupted")

        if (lower.nullable && !upper.nullable) {
            when {
                upper.base is TypeVar -> addNil(upper.base)
                lower.base == Base.Nothing -> fail("nil is not a $upper, which is not nullable")
                else -> fail("$lower may be nil, and $upper is not nullable")
            }
        }
        constrainBase(lower.base, upper)
    }

    private fun constrainBase(l: Base, upper: Type) {
        val u = upper.base
        when {
            l == Base.Nothing -> {}

            // L ≤ {K & β} iff L ≤ β and L ≤ {K}. A record on a variable never enters an upper bound.
            u is Base.OnVar -> {
                constrain(Type(l), u.base.type())
                constrain(Type(l), RecType(null, emptyList(), u.keys))
            }

            // An edge already recorded has already been propagated. (β, false) implies (β, true).
            l is TypeVar && u is TypeVar -> {
                if (l == u) return
                if (l.rigid && u.rigid) rigidFail("$l is not $u")
                val edges = upperOf(l).tvs
                val nilOk = upper.nullable && edges[u] != false
                if (edges[u] == nilOk) return
                setUpper(l, upperOf(l).copy(tvs = edges + (u to nilOk)))
                // A rigid variable's value is only itself, so it is carried as a lower bound either way, and
                // checked against what is already demanded of where it flows.
                if (!nilOk || l.rigid) addLowerVar(u, l)
                lowerOf(l).asTypes().forEach { constrain(it, u.type(nilOk)) }
                if (l.rigid) upperOf(u).asTypes().forEach { constrain(l.type(), if (nilOk) it.nullable() else it) }
            }

            // Nothing is known of a rigid variable, so it is below no concrete type.
            l is TypeVar && l.rigid -> rigidFail("$l is not a subtype of $upper")

            // Only a rigid variable and Nothing are below it. A record on a variable is below it where the variable
            // is and the keys it gives have one type each, so giving them changes nothing the variable could hold.
            u is TypeVar && u.rigid -> when {
                l is Base.OnVar && l.keys.keys.all(ctx::isMono) -> constrain(l.base.type(), u.type())
                else -> rigidFail("$l is not a subtype of $u")
            }

            l is TypeVar -> {
                val before = upperOf(l)
                addUpper(l, upper)
                if (upperOf(l) == before) return
                lowerOf(l).asTypes().forEach { constrain(it, upper) }
            }

            u is TypeVar -> {
                val before = lowerOf(u)
                val added = addLower(u, l) ?: return
                if (lowerOf(u) == before) return
                upperOf(u).asTypes().forEach { constrain(added, it) }
            }

            l is Base.OnVar -> constrainOnVar(l, upper)

            l is Base.Prim && u is Base.Prim -> if (l != u) fail("$l is not a subtype of $u")

            // The options-record convention: the runtime passes {} for an argument the caller leaves off, so a
            // function may be called without a last parameter that {} satisfies; it ignores one beyond its
            // parameters, which must be a record.
            l is Base.Fn && u is Base.Fn -> {
                val lp = l.paramTypes
                val up = u.paramTypes
                fun arityMismatch(): Nothing = fail("function arity mismatch: ${lp.size} vs ${up.size}")
                fun elided(lower: Type, upper: Type) = try {
                    constrain(lower, upper)
                } catch (e: TypeCheckException) {
                    if (e is RigidBoundException) throw e
                    arityMismatch()
                }
                when (lp.size - up.size) {
                    0 -> {}
                    1 -> elided(AnyRecord, lp.last())
                    -1 -> elided(up.last(), AnyRecord)
                    else -> arityMismatch()
                }
                lp.zip(up).forEach { (pl, pu) -> constrain(pu, pl) }
                constrain(l.returnType, u.returnType)
            }

            l is Base.Vector && u is Base.Vector -> constrain(l.el, u.el)
            l is Base.Set && u is Base.Set -> constrain(l.el, u.el)

            l is Base.Host && u is Base.Host -> constrainHost(l.applied(), u.applied())

            // Protocol types are covariant in the element: they are read through.
            u is Base.Iterable -> constrain(iterableEl(l) ?: fail("$l is not a subtype of $u"), u.el)
            u is Base.Iterator -> constrain(iteratorEl(l) ?: fail("$l is not a subtype of $u"), u.el)

            l is Base.Rec && u is Base.Rec -> constrainRec(l, u)

            else -> fail("$l is not a subtype of $u")
        }
    }

    // Between records:
    // - the name narrows: a tag is below its enum, and both are below no name;
    // - the name's arguments compare as the keys they instantiate vary with them;
    // - every key u names is carried, certainly where u demands it, at an instance below u's; a key l does not
    //   mention is absent from a closed l, and from an open one may be there at any instance, which only a key
    //   of one type satisfies;
    // - a closed u takes no key it does not mention.
    private fun constrainRec(l: Base.Rec, u: Base.Rec) {
        if (!ctx.nameBelow(l.name, u.name)) fail("$l is not a subtype of $u")
        if (u.name != null) argsBy(ctx.paramVariance(l.name!!), l.args, u.args, ::constrainBy)
        val lSlots = ctx.slotsOf(l)
        val missing = u.keys.filter { (k, s) -> s.required && lSlots[k]?.required != true }.keys
        if (missing.isNotEmpty()) fail("$l lacks ${RecordType(missing)}")
        for ((k, su) in u.keys) {
            val sl = lSlots[k]
            when {
                sl != null -> sl.instances.forEach { constrainInstance(k, it, su.args) }
                !l.closed && !ctx.isMono(k) -> fail("$l may carry ${k.toDisplayString()} at any type")
            }
        }
        if (u.closed) {
            if (!l.closed && ctx.mayCarryKeys(l.name)) fail("$l may carry keys $u does not")
            val beyond = lSlots.keys - ctx.slotsOf(u).keys
            if (beyond.isNotEmpty()) fail("$l carries ${RecordType(beyond)}, which $u does not")
        }
    }

    private fun constrainInstance(key: QSymbol, l: List<Type>, u: List<Type>) = argsBy(ctx.keyVariance(key), l, u, ::constrainBy)

    private fun constrainBy(v: Variance, l: Type, u: Type) = when (v) {
        Variance.CO -> constrain(l, u)
        Variance.CONTRA -> constrain(u, l)
        Variance.INV -> invariant(l, u)
        Variance.PHANTOM -> {}
    }

    private fun <R> argsBy(vs: List<Variance>, a: List<Type>, b: List<Type>, f: (Variance, Type, Type) -> R): List<R> {
        if (a.size != b.size || a.size != vs.size) fail("type argument count mismatch: $a vs $b")
        return vs.indices.map { i -> f(vs[i], a[i], b[i]) }
    }

    // A generic class written without its arguments, as a receiver or a type form naming only the class, has
    // fresh ones wherever it is compared: it is compatible with any arguments, as a Java raw type is.
    private fun Base.Host.applied(): Base.Host =
        if (args.isEmpty()) copy(args = List(HostTypeHierarchy.arity(className)) { freshVar() }) else this

    // The subclass's arguments carried up to the superclass, or null where it is not a subclass.
    private fun Base.Host.argsAs(superClass: String): List<Type>? =
        if (className == superClass) args else HostTypeHierarchy.supertypeArgs(className, superClass)?.map { it.type(args) }

    // A supertype's argument as a type: the subclass's argument, a class, or anything where Java does not say.
    private fun HostTypeHierarchy.Arg.type(subArgs: List<Type>): Type = when (this) {
        is HostTypeHierarchy.Arg.Param -> subArgs.getOrNull(index) ?: freshVar()
        is HostTypeHierarchy.Arg.Class -> hostClassType(name, args.map { it.type(subArgs) })
        HostTypeHierarchy.Arg.Unknown -> freshVar()
    }

    // Host arguments are invariant. Across classes the subclass's arguments are carried up the hierarchy.
    private fun constrainHost(lower: Base.Host, upper: Base.Host) {
        val mapped = lower.argsAs(upper.className) ?: fail("$lower is not a subtype of $upper")
        if (mapped.size != upper.args.size) fail("type argument count mismatch: $lower vs $upper")
        mapped.zip(upper.args).forEach { (l, u) -> invariant(l, u) }
    }

    // What a value of this base yields read as an Iterable, or as an Iterator, or null where it cannot be read
    // so. A host class reaches a protocol type through the Java interface behind it.
    private fun iterableEl(b: Base): Type? = when (b) {
        is Base.Vector -> b.el
        is Base.Set -> b.el
        is Base.Iterable -> b.el
        is Base.Host -> b.applied().argsAs(J_L_ITERABLE)?.single()
        else -> null
    }

    private fun iteratorEl(b: Base): Type? = when (b) {
        is Base.Iterator -> b.el
        is Base.Host -> b.applied().argsAs(J_U_ITERATOR)?.single()
        else -> null
    }

    private fun invariant(a: Type, b: Type) {
        constrain(a, b)
        constrain(b, a)
    }

    // {K & β} ≤ U: the keys K gives meet U's demands of them, and β meets the rest. U's name is demanded of β
    // whole, keys K gives included; a closed U lets β carry K's keys at any instance, as K replaces them.
    private fun constrainOnVar(l: Base.OnVar, upper: Type) {
        when (val u = upper.base) {
            is Base.Rec -> {
                val declared = u.name?.let { ctx.declared(it, u.args) }.orEmpty()
                for ((k, s) in l.keys) {
                    u.keys[k]?.let { constrainInstance(k, s.args, it.args) }
                    declared[k]?.instances?.forEach { constrainInstance(k, s.args, it) }
                }
                // A key given perhaps is certain only where the variable carries it.
                val rest = u.keys.filter { (k, su) -> l.keys[k]?.let { su.required && !it.required } ?: true }
                // A record on a variable is a record, so an open demand for no name and no more keys is met already.
                if (u.name == null && !u.closed && rest.isEmpty()) return
                val given = if (u.closed) l.keys.mapValues { (k, _) -> Slot(false, ctx.freshInstance(k)) } else emptyMap()
                constrain(l.base.type(), Type(u.copy(keys = rest + given)))
            }
            Base.Nothing -> constrain(l.base.type(), upper)
            is Base.Prim, is Base.Fn, is Base.Vector, is Base.Set, is Base.Iterable, is Base.Iterator, is Base.Host,
            is Base.OnVar, is TypeVar -> fail("a record is not a $u")
        }
    }

    private fun addNil(tv: TypeVar) {
        if (tv.rigid) rigidFail("nil is not a $tv")
        val b = lowerOf(tv)
        if (b.nullable) return
        setLower(tv, b.copy(concrete = b.concrete.nullable()))
        upperOf(tv).asTypes().forEach { constrain(NothingType.nullable(), it) }
    }

    // {K & α} ∨ α is α, where K's keys have one type each.
    private fun addLowerVar(tv: TypeVar, v: TypeVar) {
        val b = lowerOf(tv)
        val absorbed = b.meets[v]?.keys?.all(ctx::isMono) == true
        setLower(tv, b.copy(tvs = b.tvs + v, meets = if (absorbed) b.meets - v else b.meets))
    }

    private fun isRecordKind(b: Base) = b == Base.Nothing || (b is Base.Rec && ctx.isRecord(b.name))

    // Adds a non-nil, non-variable lower bound, returning the disjunct to check against the upper bounds:
    // the merged concrete type, the normalised record on a variable, or null when the bound was absorbed.
    private fun addLower(tv: TypeVar, l: Base): Type? {
        val b = lowerOf(tv)
        if (l is Base.OnVar) {
            if ((l.base == tv || l.base in b.tvs) && l.keys.keys.all(ctx::isMono)) return null
            if (!isRecordKind(b.concrete.base)) fail("Cannot join ${b.concrete} with $l")
            val keys = b.meets[l.base]?.let { joinGiven(it, l.keys, l.base) } ?: l.keys
            setLower(tv, b.copy(meets = b.meets + (l.base to keys)))
            return OnVarType(keys, l.base)
        }
        if (b.meets.isNotEmpty() && !isRecordKind(l)) fail("Cannot join a record with $l")
        setLower(tv, b.copy(concrete = join(b.concrete, Type(l))))
        return Type(lowerOf(tv).concrete.base)
    }

    // {K1 & α} ∨ {K2 & α}: the keys both give. A key only one gives is α's own in the other, which is its type
    // only where it has one type.
    private fun joinGiven(a: Map<QSymbol, Slot>, b: Map<QSymbol, Slot>, base: TypeVar): Map<QSymbol, Slot> {
        val out = LinkedHashMap<QSymbol, Slot>()
        for (k in a.keys + b.keys) {
            val sa = a[k]
            val sb = b[k]
            when {
                sa != null && sb != null -> out[k] = Slot(sa.required && sb.required, joinInstances(k, listOf(sa.args, sb.args)))
                !ctx.isMono(k) -> fail("Cannot join ${OnVarType(a, base)} with ${OnVarType(b, base)}: ${k.toDisplayString()} may differ from $base's own")
            }
        }
        return out
    }

    private fun addUpper(tv: TypeVar, t: Type) {
        val b = upperOf(tv)
        setUpper(tv, b.copy(concrete = b.concrete?.let { meet(it, t) } ?: t))
    }

    private fun join(a: Type, b: Type): Type = Type(joinBase(a.base, b.base), a.nullable || b.nullable)

    // Structured types merge component-wise through a fresh variable, so the result stays a single bound.
    private fun joinBase(a: Base, b: Base): Base = when {
        a == b -> a
        a == Base.Nothing -> b
        b == Base.Nothing -> a
        a is Base.Fn && b is Base.Fn -> {
            if (a.paramTypes.size != b.paramTypes.size) fail("Cannot join $a with $b: arity differs")
            Base.Fn(
                a.paramTypes.zip(b.paramTypes).map { (pa, pb) -> meetVia(pa, pb) },
                joinVia(a.returnType, b.returnType),
            )
        }
        a is Base.Vector && b is Base.Vector -> Base.Vector(joinVia(a.el, b.el))
        a is Base.Set && b is Base.Set -> Base.Set(joinVia(a.el, b.el))
        a is Base.Host && b is Base.Host -> hostJoin(a.applied(), b.applied()) ?: protocolJoin(a, b)
        a is Base.Rec && b is Base.Rec -> recJoin(a, b)
        else -> protocolJoin(a, b)
    }

    // Vectors, sets and iterable host classes are Iterables, so two that are not one constructor join to it.
    private fun protocolJoin(a: Base, b: Base): Base {
        iterableEl(a)?.let { ea -> iterableEl(b)?.let { eb -> return Base.Iterable(joinVia(ea, eb)) } }
        iteratorEl(a)?.let { ea -> iteratorEl(b)?.let { eb -> return Base.Iterator(joinVia(ea, eb)) } }
        fail("Cannot join $a with $b")
    }

    // The least name both are below, at its arguments joined as the keys they instantiate vary; a record is
    // a tag with no name, so two with none in common join to no name. Keys beyond the name's:
    // - a key both carry, certainly where both do, at the join of their instances;
    // - a key one carries, maybe, where the other is closed and so lacks it, or the key has one type;
    // - any other is unknown, as the join of an open record is open.
    private fun recJoin(a: Base.Rec, b: Base.Rec): Base {
        val name = ctx.commonName(a.name, b.name)
        if (name == null && !(ctx.isRecord(a.name) && ctx.isRecord(b.name))) fail("Cannot join $a with $b")
        val args = name?.let { namedArgs(it, a, b, ::joinBy) }.orEmpty()
        val implied = name?.let { ctx.declared(it, args).keys }.orEmpty()
        val sa = ctx.slotsOf(a)
        val sb = ctx.slotsOf(b)
        val keys = LinkedHashMap<QSymbol, Slot>()
        for (k in sa.keys + sb.keys) {
            if (k in implied) continue
            val fa = sa[k]
            val fb = sb[k]
            val instances = (fa?.instances.orEmpty() + fb?.instances.orEmpty()).toList()
            when {
                fa != null && fb != null -> keys[k] = Slot(fa.required && fb.required, joinInstances(k, instances))
                (fa != null && b.closed) || (fb != null && a.closed) || ctx.isMono(k) -> keys[k] = Slot(false, joinInstances(k, instances))
            }
        }
        return Base.Rec(name, args, keys, a.closed && b.closed)
    }

    // The arguments of [name], which both a's and b's names are below, from theirs. An argument a side's own
    // name does not use, as a nullary variant's, says nothing about the value, so the other side's is taken.
    private fun namedArgs(name: Name, a: Base.Rec, b: Base.Rec, by: (Variance, Type, Type) -> Type): List<Type> {
        val v = ctx.paramVariance(name)
        val va = ctx.paramVariance(a.name!!)
        val vb = ctx.paramVariance(b.name!!)
        if (a.args.size != v.size || b.args.size != v.size) fail("type argument count mismatch: $a vs $b")
        return v.indices.map { i ->
            when {
                va[i] == Variance.PHANTOM -> b.args[i]
                vb[i] == Variance.PHANTOM -> a.args[i]
                else -> by(v[i], a.args[i], b.args[i])
            }
        }
    }

    private fun joinInstances(key: QSymbol, instances: List<List<Type>>): List<Type> =
        instances.reduce { x, y -> argsBy(ctx.keyVariance(key), x, y, ::joinBy) }

    private fun joinBy(v: Variance, a: Type, b: Type): Type = when (v) {
        Variance.CO, Variance.PHANTOM -> joinVia(a, b)
        Variance.CONTRA -> meetVia(a, b)
        Variance.INV -> invVia(a, b)
    }

    private fun meetBy(v: Variance, a: Type, b: Type): Type = when (v) {
        Variance.CO, Variance.PHANTOM -> meetVia(a, b)
        Variance.CONTRA -> joinVia(a, b)
        Variance.INV -> invVia(a, b)
    }

    // Host classes join along the class chain, or null where neither is the other's superclass: Java's
    // interfaces give no one least upper bound.
    private fun hostJoin(a: Base.Host, b: Base.Host): Base.Host? {
        a.argsAs(b.className)?.let { return Base.Host(b.className, invArgs(it, b.args)) }
        b.argsAs(a.className)?.let { return Base.Host(a.className, invArgs(it, a.args)) }
        return null
    }

    private fun invArgs(a: List<Type>, b: List<Type>): List<Type> {
        if (a.size != b.size) fail("type argument count mismatch: $a vs $b")
        return a.zip(b).map { (x, y) -> invVia(x, y) }
    }

    private fun meet(a: Type, b: Type): Type = Type(meetBase(a.base, b.base), a.nullable && b.nullable)

    // Across kinds the meet is Nothing: nothing can satisfy both, and the error surfaces when something tries to.
    private fun meetBase(a: Base, b: Base): Base = when {
        a == b -> a
        a == Base.Nothing || b == Base.Nothing -> Base.Nothing
        a is Base.Fn && b is Base.Fn -> {
            if (a.paramTypes.size != b.paramTypes.size) Base.Nothing
            else Base.Fn(
                a.paramTypes.zip(b.paramTypes).map { (pa, pb) -> joinVia(pa, pb) },
                meetVia(a.returnType, b.returnType),
            )
        }
        a is Base.Vector && b is Base.Vector -> Base.Vector(meetVia(a.el, b.el))
        a is Base.Set && b is Base.Set -> Base.Set(meetVia(a.el, b.el))
        a is Base.Iterable && b is Base.Iterable -> Base.Iterable(meetVia(a.el, b.el))
        a is Base.Iterator && b is Base.Iterator -> Base.Iterator(meetVia(a.el, b.el))
        a is Base.Host && b is Base.Host -> hostMeet(a.applied(), b.applied())
        b is Base.Iterable || b is Base.Iterator -> protocolMeet(a, b)
        a is Base.Iterable || a is Base.Iterator -> protocolMeet(b, a)
        a is Base.Rec && b is Base.Rec -> recMeet(a, b)
        else -> Base.Nothing
    }

    // The subclass, its arguments carried up equal to the superclass's.
    private fun hostMeet(a: Base.Host, b: Base.Host): Base {
        if (a.className == b.className) return Base.Host(a.className, invArgs(a.args, b.args))
        a.argsAs(b.className)?.let { equateArgs(it, b.args); return a }
        b.argsAs(a.className)?.let { equateArgs(it, a.args); return b }
        return Base.Nothing
    }

    private fun equateArgs(a: List<Type>, b: List<Type>) {
        if (a.size != b.size) fail("type argument count mismatch: $a vs $b")
        a.zip(b).forEach { (x, y) -> invariant(x, y) }
    }

    // A value read as a protocol type too: its element is demanded to be the protocol's. A host class's is
    // fixed by its invariant arguments, so the demand is a constraint on them rather than a narrower type.
    private fun protocolMeet(a: Base, protocol: Base): Base = when (protocol) {
        is Base.Iterable -> when (a) {
            is Base.Vector -> Base.Vector(meetVia(a.el, protocol.el))
            is Base.Set -> Base.Set(meetVia(a.el, protocol.el))
            is Base.Host -> iterableEl(a)?.let { constrain(it, protocol.el); a } ?: Base.Nothing
            else -> Base.Nothing
        }
        is Base.Iterator -> when (a) {
            is Base.Host -> iteratorEl(a)?.let { constrain(it, protocol.el); a } ?: Base.Nothing
            else -> Base.Nothing
        }
        else -> Base.Nothing
    }

    // The more specific name, or Nothing where none is below both, at its arguments met. Keys beyond it:
    // - a key either demands, certainly where either does, at the meet of the instances demanded;
    // - a key the name declares is demanded of its instance there, so the name's arguments carry it;
    // - a closed side has no key it does not mention, so a key only the other demands is dropped where it may
    //   be absent, and leaves nothing where it must be present.
    private fun recMeet(a: Base.Rec, b: Base.Rec): Base {
        val specific = ctx.specificName(a.name, b.name)
        if (specific == null && (a.name != null || b.name != null)) return Base.Nothing
        // A record demanded of a name that is not one: of an enum, its one variant with a record, where there is one.
        val name = if (ctx.isRecord(specific) || (a.name != null && b.name != null)) specific
            else ctx.onlyRecordVariant(specific!!) ?: return Base.Nothing
        val args = when {
            a.name != null && b.name != null -> namedArgs(name!!, a, b, ::meetBy)
            a.name != null -> a.args
            else -> b.args
        }
        val implied = name?.let { ctx.declared(it, args) }.orEmpty()
        // A closed side mentions every key it may carry, so a name declaring others is below nothing it is.
        for (side in listOf(a, b)) if (side.closed && implied.keys.any { it !in ctx.slotsOf(side) }) return Base.Nothing
        val keys = LinkedHashMap<QSymbol, Slot>()
        for (k in a.keys.keys + b.keys.keys) {
            val sa = a.keys[k]
            val sb = b.keys[k]
            val demanded = listOfNotNull(sa, sb)
            val required = demanded.any { it.required }
            if ((sa == null && a.closed && k !in implied) || (sb == null && b.closed && k !in implied)) {
                if (required) return Base.Nothing
                continue
            }
            implied[k]?.let { f ->
                demanded.forEach { s -> f.instances.forEach { constrainInstance(k, it, s.args) } }
                if (required && !f.required) keys[k] = Slot(true, demanded.first { it.required }.args)
                continue
            }
            keys[k] = Slot(required, demanded.map { it.args }.reduce { x, y -> argsBy(ctx.keyVariance(k), x, y, ::meetBy) })
        }
        return Base.Rec(name, args, keys, a.closed || b.closed)
    }

    // A join or meet of two types is a fresh variable bounded by both, unless one side is a variable
    // already known to bound the other: then it is that variable, so a bound flowing round a cycle
    // settles instead of minting a variable on every pass.
    private fun joinVia(a: Type, b: Type): Type = when {
        a == b -> a
        b.bareVar?.let { isBelow(a, it) } == true -> b
        a.bareVar?.let { isBelow(b, it) } == true -> a
        else -> freshVar().also { constrain(a, it); constrain(b, it) }
    }

    private fun meetVia(a: Type, b: Type): Type = when {
        a == b -> a
        b.bareVar?.let { isAbove(a, it) } == true -> b
        a.bareVar?.let { isAbove(b, it) } == true -> a
        else -> freshVar().also { constrain(it, a); constrain(it, b) }
    }

    private fun isBelow(t: Type, v: TypeVar): Boolean =
        lowerOf(v).let { lo -> t.bareVar?.let { it in lo.tvs } ?: (lo.concrete == t) }

    private fun isAbove(t: Type, v: TypeVar): Boolean =
        upperOf(v).let { up -> (t.base as? TypeVar)?.let { up.tvs[it] == t.nullable } ?: (up.concrete == t) }

    private fun invVia(a: Type, b: Type): Type = when {
        a == b -> a
        b.bareVar?.let { isBelow(a, it) && isAbove(a, it) } == true -> b
        a.bareVar?.let { isBelow(b, it) && isAbove(b, it) } == true -> a
        else -> freshVar().also { invariant(a, it); invariant(b, it) }
    }
}
