package brj.types

import brj.runtime.QSymbol

// A scheme reduced for display. The passes, in order:
// 1. Occurrences: where each variable the type reaches occurs, positively and negatively, and which variables
//    always occur together.
// 2. Merging: variables below one another are one variable, as are variables that always occur together.
// 3. Resolution: a variable only ever produced is what flows into it, and one only ever consumed is its one
//    demand; any other is kept, and named.
// 4. Constraints: each kept variable's bounds, with the unnamed variables between kept ones composed away.
// 5. Folding: a bound the type can say itself is written there rather than as a constraint.
fun Scheme.simplify(ctx: TypeCtx = TypeCtx.EMPTY): Rendered = Simplifier(this, ctx).run()

private class Simplifier(scheme: Scheme, private val ctx: TypeCtx) {
    private val root = scheme.type
    private val bounds = scheme.bounds

    private fun lowerOf(v: TypeVar) = bounds.lower(v)
    private fun upperOf(v: TypeVar) = bounds.upper(v)

    private fun UpperBound.strictTvs() = tvs.filterValues { !it }.keys
    private fun UpperBound.nilOkTvs() = tvs.filterValues { it }.keys
    private fun LowerBound.concreteBase(): Base? = concrete.base.takeIf { it != Base.Nothing }

    // --- Variance, where the context knows it. A name or key it does not know, as in a declared type
    // rendered without one, varies both ways in every argument, which keeps that argument's variables.

    private fun nameVariance(name: Name, n: Int): List<Variance> =
        runCatching { ctx.paramVariance(name) }.getOrNull()?.takeIf { it.size == n } ?: List(n) { Variance.INV }

    private fun keyVariance(key: QSymbol, n: Int): List<Variance> =
        runCatching { ctx.keyVariance(key) }.getOrNull()?.takeIf { it.size == n } ?: List(n) { Variance.INV }

    // Whether an argument varies with its type (true), against it (false), or both ways (null). A phantom
    // argument is taken as varying both ways, which keeps its variables.
    private fun Variance.polarity(): Boolean? = when (this) {
        Variance.CO -> true
        Variance.CONTRA -> false
        Variance.INV, Variance.PHANTOM -> null
    }

    // The polarity of an argument at [pos], given its own.
    private fun under(pos: Boolean?, p: Boolean?): Boolean? = if (p == null || pos == null) null else if (p) pos else !pos

    private fun Map<QSymbol, Slot>.mapArgs(f: (Type, Boolean?) -> Type): Map<QSymbol, Slot> = mapValues { (k, s) ->
        if (s.args.isEmpty()) s
        else s.copy(args = s.args.zip(keyVariance(k, s.args.size)).map { (a, v) -> f(a, v.polarity()) })
    }

    // Every type argument of a record, its name's and its keys', with its polarity.
    private fun Base.Rec.mapArgs(f: (Type, Boolean?) -> Type): Base.Rec = copy(
        args = if (args.isEmpty()) args else args.zip(nameVariance(name!!, args.size)).map { (a, v) -> f(a, v.polarity()) },
        keys = keys.mapArgs(f),
    )

    // A type's immediate parts, each with its polarity relative to the type's own: true where it varies with
    // it, false against it, null both ways. A record on a variable's variable is not a part: callers see it.
    private fun Type.parts(): List<Pair<Type, Boolean?>> = when (val b = base) {
        is Base.Fn -> b.paramTypes.map { it to false } + (b.returnType to true)
        is Base.Host -> b.args.map { it to null }
        is Base.Rec -> buildList { b.mapArgs { a, p -> add(a to p); a } }
        is Base.OnVar -> buildList { b.keys.mapArgs { a, p -> add(a to p); a } }
        is Base.Vector -> listOf(b.el to true)
        is Base.Set -> listOf(b.el to true)
        is Base.Iterable -> listOf(b.el to true)
        is Base.Iterator -> listOf(b.el to true)
        is Base.Prim, Base.Nothing, is TypeVar -> emptyList()
    }

    // The type with each part replaced, at its polarity under [pos].
    private fun Type.mapParts(pos: Boolean?, f: (Type, Boolean?) -> Type): Type = when (val b = base) {
        is Base.Fn -> copy(base = Base.Fn(b.paramTypes.map { f(it, pos?.not()) }, f(b.returnType, pos)))
        is Base.Host -> copy(base = Base.Host(b.className, b.args.map { f(it, null) }))
        is Base.Rec -> copy(base = b.mapArgs { a, p -> f(a, under(pos, p)) })
        is Base.OnVar -> copy(base = b.copy(keys = b.keys.mapArgs { a, p -> f(a, under(pos, p)) }))
        else -> copy(base = base.mapTypes { f(it, pos) })
    }

    // --- 1. Occurrences.

    private val posOcc = LinkedHashSet<TypeVar>()
    private val negOcc = LinkedHashSet<TypeVar>()
    private val coPos = HashMap<TypeVar, MutableSet<TypeVar>>()
    private val coNeg = HashMap<TypeVar, MutableSet<TypeVar>>()
    private val walked = HashSet<Pair<TypeVar, Boolean>>()
    private val nilWalked = HashSet<TypeVar>()

    // The variables a variable stands for at a polarity: itself and what flows in (positive) or what is
    // demanded of it (negative), through variable edges.
    private fun reach(v: TypeVar, pos: Boolean): Set<TypeVar> {
        val out = LinkedHashSet<TypeVar>()
        val pending = ArrayDeque(listOf(v))
        while (pending.isNotEmpty()) {
            val x = pending.removeFirst()
            if (!out.add(x)) continue
            pending += if (pos) lowerOf(x).let { it.tvs + it.meets.keys } else upperOf(x).strictTvs()
        }
        return out
    }

    private fun walk(t: Type, pos: Boolean) {
        when (val b = t.base) {
            is TypeVar -> site(b, pos)
            is Base.OnVar -> site(b.base, pos)
            else -> {}
        }
        for ((part, p) in t.parts()) {
            if (p != false) walk(part, pos)
            if (p != true) walk(part, !pos)
        }
    }

    private fun site(v: TypeVar, pos: Boolean) {
        val s = reach(v, pos)
        val occ = if (pos) posOcc else negOcc
        val co = if (pos) coPos else coNeg
        for (w in s) {
            occ += w
            co[w]?.retainAll(s) ?: run { co[w] = s.toMutableSet() }
        }
        // Concrete bounds carry variables of their own.
        for (w in s) {
            if (!walked.add(w to pos)) continue
            if (pos) lowerOf(w).concreteType()?.let { walk(it, true) }
            else upperOf(w).concrete?.let { walk(it, false) }
        }
        // α ≤ β? makes β a demand on α, but α is not β: an occurrence, not a co-occurrence.
        if (!pos) for (w in s) upperOf(w).nilOkTvs().forEach { if (nilWalked.add(it)) site(it, false) }
    }

    // --- 2. Merging.

    private val vars: Set<TypeVar> by lazy { posOcc + negOcc + bounds.keys.sortedBy { it.id } }

    private val parent = HashMap<TypeVar, TypeVar>()

    private fun find(v: TypeVar): TypeVar {
        var x = v
        while (true) x = parent[x] ?: return x
    }

    private fun union(a: TypeVar, b: TypeVar) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[rb] = ra
    }

    // Variables below one another through variable edges are mutual subtypes, so they are one variable.
    private fun unifyCycles() {
        val below = HashMap<TypeVar, Set<TypeVar>>()
        fun below(v: TypeVar): Set<TypeVar> = below.getOrPut(v) {
            val out = HashSet<TypeVar>()
            val pending = ArrayDeque(lowerOf(v).tvs)
            while (pending.isNotEmpty()) {
                val x = pending.removeFirst()
                if (out.add(x)) pending += lowerOf(x).tvs
            }
            out
        }
        for (v in vars) {
            for (w in below(v)) if (w != v && v in below(w)) union(v, w)
        }
    }

    // Variables that each occur wherever the other does are one variable, where the merged variables' concrete
    // bounds agree: a merged variable has one of each, and taking one of two would drop the other. Variables
    // below one another already agree, as the solver pushes each one's lower bounds into the other.
    // - Together at both polarities, they are indistinguishable.
    // - Together at one polarity only, as the results of two calls joined are, they are one variable where their
    //   bounds the other way are the same: there their occurrences are apart, and each would take on the other's.
    // Where a ≤ c and b ≤ c, every site demanding a demands c, but c is also demanded where b is, without a: a
    // does not occur wherever c does, and merging them would put a's demands on b.
    private val classLower = HashMap<TypeVar, Base>()
    private val classUpper = HashMap<TypeVar, Base>()

    private fun unifyCoOccurring() {
        for (m in vars) {
            val r = find(m)
            lowerOf(m).concreteBase()?.let { classLower.putIfAbsent(r, it) }
            upperOf(m).concrete?.base?.let { classUpper.putIfAbsent(r, it) }
        }
        fun agree(a: Base?, b: Base?) = a == null || b == null || a == b
        fun merge(rv: TypeVar, rw: TypeVar) {
            union(rv, rw)
            classLower[rw]?.let { classLower.putIfAbsent(rv, it) }
            classUpper[rw]?.let { classUpper.putIfAbsent(rv, it) }
        }
        fun both(v: TypeVar) = (coPos[v] ?: emptySet<TypeVar>()) intersect (coNeg[v] ?: emptySet<TypeVar>())
        for (v in posOcc intersect negOcc) {
            for (w in both(v)) {
                if (v !in both(w)) continue
                val rv = find(v)
                val rw = find(w)
                if (rv == rw) continue
                if (!agree(classLower[rv], classLower[rw]) || !agree(classUpper[rv], classUpper[rw])) continue
                merge(rv, rw)
            }
        }
        for (pos in listOf(true, false)) {
            val co = if (pos) coPos else coNeg
            for (v in if (pos) posOcc else negOcc) {
                for (w in co[v].orEmpty()) {
                    if (co[w]?.contains(v) != true) continue
                    val rv = find(v)
                    val rw = find(w)
                    if (rv == rw) continue
                    val merging = setOf(rv, rw)
                    val apart = if (pos) upperSig(rv, merging) == upperSig(rw, merging) else lowerSig(rv, merging) == lowerSig(rw, merging)
                    val together = if (pos) agree(classLower[rv], classLower[rw]) else agree(classUpper[rv], classUpper[rw])
                    if (apart && together) merge(rv, rw)
                }
            }
        }
    }

    // A merged variable's bounds one way, as far as its variable edges reach, less the variables being merged
    // with it: through b ≤ c, what c is demanded to be, b is already.
    private fun closure(r: TypeVar, next: (TypeVar) -> Iterable<TypeVar>): Set<TypeVar> {
        val out = LinkedHashSet<TypeVar>()
        val pending = ArrayDeque(vars.filter { find(it) == r })
        while (pending.isNotEmpty()) {
            val x = pending.removeFirst()
            if (out.add(x)) pending += next(x)
        }
        return out
    }

    private fun upperSig(r: TypeVar, merging: Set<TypeVar>): Pair<Set<Type>, Set<Pair<TypeVar, Boolean>>> {
        val us = closure(r) { upperOf(it).tvs.keys }.map { upperOf(it) }
        return us.mapNotNull { it.concrete }.toSet() to
            us.flatMap { it.tvs.entries }.map { (k, nilOk) -> find(k) to nilOk }.filter { it.first !in merging }.toSet()
    }

    private fun lowerSig(r: TypeVar, merging: Set<TypeVar>): Triple<Set<Type>, Set<TypeVar>, Set<Pair<TypeVar, Map<QSymbol, Slot>>>> {
        val ls = closure(r) { lowerOf(it).let { l -> l.tvs + l.meets.keys } }.map { lowerOf(it) }
        return Triple(
            ls.mapNotNull { it.concreteType() }.toSet(),
            ls.flatMap { it.tvs }.map(::find).filter { it !in merging }.toSet(),
            ls.flatMap { it.meets.entries }.map { (k, keys) -> find(k) to keys }.filter { it.first !in merging }.toSet(),
        )
    }

    private val members: Map<TypeVar, List<TypeVar>> by lazy { vars.groupBy { find(it) } }

    private fun memberList(rep: TypeVar) = members[rep] ?: listOf(rep)

    // A merged variable's bounds are its members': their one concrete lower bound (the solver joined each
    // member's, and merged members agree on it), and their edges, to other merged variables.
    private fun mergedLower(rep: TypeVar): LowerBound {
        val ls = memberList(rep).map { lowerOf(it) }
        return LowerBound(
            concrete = Type(ls.firstNotNullOfOrNull { it.concreteBase() } ?: Base.Nothing, ls.any { it.nullable }),
            tvs = ls.flatMap { it.tvs }.map(::find).filter { it != rep }.toSet(),
            meets = ls.flatMap { it.meets.entries }.filter { find(it.key) != rep }
                .groupBy({ find(it.key) }, { it.value }).mapValues { (_, ks) -> ks.reduce { a, b -> a.filter { (k, s) -> b[k] == s } } },
        )
    }

    private fun mergedUpper(rep: TypeVar): UpperBound {
        val us = memberList(rep).map { upperOf(it) }
        return UpperBound(
            concrete = us.firstNotNullOfOrNull { it.concrete }?.copy(nullable = us.all { it.concrete?.nullable ?: true }),
            tvs = us.flatMap { it.tvs.entries }.filter { find(it.key) != rep }
                .groupBy({ find(it.key) }, { it.value }).mapValues { (_, nilOks) -> nilOks.all { it } },
        )
    }

    private fun occursPos(rep: TypeVar) = memberList(rep).any { it in posOcc }
    private fun occursNeg(rep: TypeVar) = memberList(rep).any { it in negOcc }

    // --- 3. Resolution.

    private val resolved = HashMap<TypeVar, Type>()
    private val resolving = HashSet<TypeVar>()
    private val recursive = HashSet<TypeVar>()

    // What a variable stands for in the rendered type, with the variables within it resolved in turn: itself
    // when it is kept, otherwise the one type it is determined to be. A variable met again while its own
    // resolution is being built is recursive, and so kept.
    private fun resolve(v: TypeVar): Type {
        val rep = find(v)
        resolved[rep]?.let { return it }
        if (!resolving.add(rep)) {
            recursive += rep
            return rep.type()
        }
        val candidate = when {
            occursPos(rep) && !occursNeg(rep) -> produced(rep)
            occursNeg(rep) && !occursPos(rep) -> consumed(rep)
            else -> null
        }
        resolving -= rep
        val result = if (candidate == null || rep in recursive) rep.type() else candidate
        resolved[rep] = result
        return result
    }

    // A variable only ever produced is the join of what flows into it. Through a variable edge, what flows into
    // the lower variable has already flowed into this one's concrete bound, so an edge adds only where its
    // variable is kept: then the variable is the join of that variable and the rest, which only a variable
    // alone, or with nil, can say. A record on a variable is its variable known to carry keys.
    private fun produced(rep: TypeVar): Type? {
        val lo = mergedLower(rep)
        val kept = lo.tvs.map(::resolve).filter { it.bareVar != null }.toSet()
        val records = lo.meets.map { (b, keys) -> onBase(keys.mapArgs { a, p -> subst(a, p) }, b) }
            // {K & b} ∨ b = b, where K's keys have one type each.
            .filterNot { r -> (r.base as? Base.OnVar)?.let { it.base.type() in kept && it.keys.keys.all(ctx::isMono) } == true }
        val concrete = lo.concreteBase()?.let { subst(Type(it), true) }
        val parts = kept + records + listOfNotNull(concrete)
        return when {
            parts.isEmpty() -> if (lo.nullable) NothingType.nullable() else NothingType
            parts.size == 1 -> parts.single().let { if (lo.nullable) it.nullable() else it }
            else -> null
        }
    }

    // A variable only ever consumed is its one demand, where it has one.
    private fun consumed(rep: TypeVar): Type? {
        val up = mergedUpper(rep)
        val parts = listOfNotNull(up.concrete?.let { subst(it, false) }) +
            up.tvs.map { (w, nilOk) -> resolve(w).let { if (nilOk) it.nullable() else it } }
        return parts.toSet().singleOrNull()
    }

    // --- Substitution: a type with every variable resolved. [pos] is its polarity: true at a positive
    // occurrence, false at a negative one, null where it varies both ways. A kept variable that may be nil
    // shows that at its positive occurrences.
    private fun subst(t: Type, pos: Boolean?): Type {
        val r = when (val b = t.base) {
            is TypeVar -> {
                val v = resolve(b)
                val shown = if (pos == true && v.bareVar?.let { mergedLower(it).nullable } == true) v.nullable() else v
                if (t.nullable) shown.nullable() else shown
            }
            is Base.OnVar -> {
                val keys = b.keys.mapArgs { a, p -> subst(a, under(pos, p)) }
                onBase(keys, b.base).let { if (t.nullable) it.nullable() else it }
            }
            else -> t.mapParts(pos, ::subst)
        }
        return normalise(r)
    }

    // The record on [base] with [keys]: `{K & a}` where the variable resolves to a variable, and where it
    // resolves to a record, that record with the keys. Where `with` has no type for what it resolves to, the
    // record stays on the variable, which is then kept.
    private fun onBase(keys: Map<QSymbol, Slot>, base: TypeVar): Type {
        val t = resolve(base)
        t.bareVar?.let { return normalise(OnVarType(keys, it)) }
        return runCatching { withKeys(t, keys, ctx) }.getOrNull() ?: OnVarType(keys, find(base))
    }

    // A record on a variable whose own demands already carry the keys, each of one type, is just the variable.
    private fun normalise(t: Type): Type = when (val b = t.base) {
        is Base.OnVar -> {
            val demanded = (mergedUpper(find(b.base)).concrete?.base as? Base.Rec)?.let(ctx::slotsOf)
            val implied = demanded != null && b.keys.all { (k, _) -> ctx.isMono(k) && demanded[k]?.required == true }
            if (implied) b.base.type(t.nullable) else t
        }
        else -> t
    }

    // --- 4. Constraints.

    // Variables the rendered type already shows as nullable, so their nil lower bound is not a constraint.
    private val inlineNullable = HashSet<TypeVar>()

    private fun nullableVars(t: Type) {
        (t.base as? TypeVar)?.let { if (t.nullable) inlineNullable += it }
        t.parts().forEach { (part, _) -> nullableVars(part) }
    }

    // What flows into a kept variable. A variable that is not kept is not named: its edges are composed away,
    // so a kept variable's constraints mention only concrete types and other kept variables. [keys] is what
    // the path from the kept variable has given, through records on unnamed variables.
    // [self] is the kept variable the bounds are of, which is no bound of its own.
    private fun lowersOf(self: TypeVar, v: TypeVar, kept: Set<TypeVar>, keys: Map<QSymbol, Slot>?, seen: MutableSet<TypeVar>, out: MutableSet<Type>) {
        if (!seen.add(v)) return
        val lo = mergedLower(v)
        // A lower bound reached through records on unnamed variables carries their keys. One `with` has no type
        // for, as an ill-typed scheme's may be, is shown as it is.
        fun emit(t: Type) {
            val s = if (keys == null) t else t.bareVar?.let { normalise(OnVarType(keys, it)) } ?: runCatching { withKeys(t, keys, ctx) }.getOrDefault(t)
            if (s != NothingType && s.base != self) out += s
        }
        fun edge(w: TypeVar, given: Map<QSymbol, Slot>?) {
            val s = subst(w.type(), true)
            val sv = s.bareVar
            if (sv != null && sv !in kept) lowersOf(self, sv, kept, given, seen, out) else emit(s)
        }
        val nilShown = v in inlineNullable
        (if (nilShown) lo.concreteBase()?.let { Type(it) } else lo.concreteType())?.let { emit(subst(it, true)) }
        lo.meets.forEach { (b, added) -> edge(b, keys.orEmpty() + added) }
        lo.tvs.forEach { edge(it, keys) }
    }

    // What is demanded of a kept variable, composed through unnamed variables as its lower bounds are.
    private fun uppersOf(self: TypeVar, v: TypeVar, kept: Set<TypeVar>, seen: MutableSet<TypeVar>, out: MutableSet<Type>) {
        if (!seen.add(v)) return
        val up = mergedUpper(v)
        up.concrete?.let { out += subst(it, false) }
        up.tvs.forEach { (w, nilOk) ->
            val s = subst(w.type(), false)
            val sv = s.bareVar
            when {
                s.base == self -> {}
                nilOk -> out += s.nullable()
                sv != null && sv !in kept -> {
                    uppersOf(self, sv, kept, seen, out)
                    joinDemand(sv)?.let { out += it }
                }
                else -> out += s
            }
        }
    }

    // What flows into a variable must join with what else does, and there is no top to join to: into one
    // holding an Int only an Int may flow, and into one holding a record only a record.
    private fun joinDemand(v: TypeVar): Type? {
        val lo = mergedLower(v)
        val demand = when (val c = lo.concreteBase()) {
            is Base.Prim -> Type(c)
            is Base.Rec -> AnyRecord.takeIf { ctx.isRecord(c.name) }
            else -> null
        }
        return demand?.let { if (lo.nullable) it.nullable() else it }
    }

    // Demands of one kind on one variable, composed through unnamed variables, are one demand where their
    // elements can be met for display: an unnamed variable with no demands is anything.
    private fun mergeUppers(ts: Set<Type>, kept: Set<TypeVar>): List<Type> {
        // Nothing is demanded of it, or of anything it flows into.
        fun free(t: Type): Boolean {
            val tv = t.bareVar ?: return false
            val seen = HashSet<TypeVar>()
            val pending = ArrayDeque(listOf(find(tv)))
            while (pending.isNotEmpty()) {
                val x = pending.removeFirst()
                if (!seen.add(x)) continue
                if (x in kept || mergedUpper(x).concrete != null) return false
                pending += mergedUpper(x).tvs.keys.map(::find)
            }
            return true
        }
        fun meet(a: Type, b: Type): Type? {
            if (a == b) return a
            if (free(a)) return b
            if (free(b)) return a
            if (a.nullable != b.nullable) return null
            val ba = a.base
            val bb = b.base
            val m = when {
                ba is Base.Vector && bb is Base.Vector -> meet(ba.el, bb.el)?.let { Base.Vector(it) }
                ba is Base.Set && bb is Base.Set -> meet(ba.el, bb.el)?.let { Base.Set(it) }
                ba is Base.Iterable && bb is Base.Iterable -> meet(ba.el, bb.el)?.let { Base.Iterable(it) }
                ba is Base.Rec && ba.name == null && !ba.closed && bb is Base.Rec && bb.name == null && !bb.closed &&
                    (ba.keys.keys intersect bb.keys.keys).all { ba.keys[it] == bb.keys[it] } ->
                    Base.Rec(null, emptyList(), ba.keys + bb.keys, false)
                else -> null
            }
            return m?.let { Type(it, a.nullable) }
        }
        val out = ArrayList<Type>()
        outer@ for (t in ts) {
            for (i in out.indices) {
                val m = meet(out[i], t)
                if (m != null) { out[i] = m; continue@outer }
            }
            out += t
        }
        return out
    }

    fun run(): Rendered {
        walk(root, pos = true)
        unifyCycles()
        unifyCoOccurring()

        val type = subst(root, pos = true)
        nullableVars(type)

        // The variables kept, in order of appearance: those in the type, and those in the constraints it takes
        // to render them. Composing unnamed variables away can surface a variable inside a concrete bound, so
        // this closes to a fixpoint.
        val kept = LinkedHashSet<TypeVar>(type.typeVars())
        var constraints: List<Pair<Type, Type>>
        while (true) {
            val found = LinkedHashSet<Pair<Type, Type>>()
            for (v in kept) {
                val lowers = LinkedHashSet<Type>()
                lowersOf(v, v, kept, null, HashSet(), lowers)
                lowers.forEach { found += it to v.type() }
                val uppers = LinkedHashSet<Type>()
                uppersOf(v, v, kept, HashSet(), uppers)
                mergeUppers(uppers, kept).forEach { found += v.type() to it }
            }
            constraints = found.toList()
            val more = constraints.flatMap { (l, u) -> l.typeVars() + u.typeVars() }.filter { it !in kept }
            if (more.isEmpty()) break
            kept += more
        }

        // A constraint that only restates a variable's own bound in the other direction is noise.
        val deduped = constraints.filterNot { (l, u) ->
            val lv = l.bareVar
            val uv = u.bareVar
            lv != null && uv != null && (u to l) in constraints && lv.id > uv.id
        }

        // A bound implied by others of the same variable says nothing more. Only bounds without variables are
        // compared, which subtyping decides alone.
        // - A lower bound below another lower bound, or an upper bound above another upper bound.
        // - `{}` above a record flowing in: with no top, what a record flows into is a record.
        fun below(a: Type, b: Type) = a.typeVars().isEmpty() && b.typeVars().isEmpty() &&
            runCatching { emptyMap<TypeVar, Bounds>().constrain(a, b, ctx) }.isSuccess
        fun strictlyBelow(a: Type, b: Type) = below(a, b) && !below(b, a)
        val pruned = deduped.filterNot { (l, u) ->
            val lowerOfVar = u.bareVar != null && l.bareVar == null &&
                deduped.any { (l2, u2) -> u2 == u && strictlyBelow(l, l2) }
            val upperOfVar = l.bareVar != null && u.bareVar == null &&
                deduped.any { (l2, u2) -> l2 == l && strictlyBelow(u2, u) }
            val recordTop = l.bareVar != null && (u == AnyRecord || u == AnyRecord.nullable()) &&
                deduped.any { (l2, u2) -> u2 == l && l2.bareVar == null && below(l2, u) }
            lowerOfVar || upperOfVar || recordTop
        }

        val (folded, left) = fold(type, pruned)
        val used = folded.typeVars() + left.flatMap { (l, u) -> l.typeVars() + u.typeVars() }
        val names = kept.filter { it in used }.withIndex().associate { (i, v) -> v to varName(i) }
        return Rendered(folded, left, names)
    }

    // --- 5. Folding: a bound the type can say itself is written there rather than as a constraint.
    // - A primitive bound is the primitive: joins are per kind, so only Nothing lies below one.
    // - A record demanded of a variable the type takes in goes on those occurrences, `{K & a}`, and a result of
    //   `{K' & a}` within those keys is `a` itself.
    private fun fold(start: Type, constraints: List<Pair<Type, Type>>): Pair<Type, List<Pair<Type, Type>>> {
        var type = start
        var left = constraints
        while (true) {
            val c = left.firstOrNull { (l, u) ->
                (u.bareVar != null && l.base is Base.Prim) || (l.bareVar != null && u.base is Base.Prim && !u.nullable)
            } ?: break
            val s = c.second.bareVar?.let { mapOf(it to c.first) } ?: mapOf(c.first.bareVar!! to c.second)
            type = type.substitute(s)
            left = (left - c).map { (l, u) -> l.substitute(s) to u.substitute(s) }
        }
        while (true) {
            val c = left.firstOrNull { (l, u) ->
                val v = l.bareVar
                val r = u.base as? Base.Rec
                v != null && r != null && r.name == null && !r.closed && r.keys.values.all { it.required && it.args.isEmpty() } &&
                    !u.nullable && polarities(type, v, true).let { false in it && null !in it }
            } ?: break
            type = carrying(type, c.first.bareVar!!, (c.second.base as Base.Rec).keys, true)
            left = left - c
        }
        return type to left
    }

    // The polarities [v] occurs at in [t], taken at [pos].
    private fun polarities(t: Type, v: TypeVar, pos: Boolean?): Set<Boolean?> {
        val here = when (val b = t.base) {
            v -> setOf(pos)
            is Base.OnVar -> if (b.base == v) setOf(pos) else emptySet()
            else -> emptySet()
        }
        return here + t.parts().flatMap { (part, p) -> polarities(part, v, under(pos, p)) }
    }

    // [t] with [v] known to carry [keys] at its negative occurrences, and a result of `{K' & v}` within those
    // keys, each of one type, as [v] itself.
    private fun carrying(t: Type, v: TypeVar, keys: Map<QSymbol, Slot>, pos: Boolean): Type = when (val b = t.base) {
        v -> if (pos) t else Type(Base.OnVar(keys, v), t.nullable)
        is Base.OnVar -> when {
            b.base != v -> t
            pos && keys.keys.containsAll(b.keys.keys) && b.keys.keys.all(ctx::isMono) -> Type(v, t.nullable)
            pos -> t
            else -> Type(Base.OnVar(b.keys + keys, v), t.nullable)
        }
        else -> t.mapParts(pos) { part, p -> if (p == null) part else carrying(part, v, keys, p) }
    }
}
