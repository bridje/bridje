package brj.types

fun Scheme.simplify(ctx: TypeCtx = TypeCtx.EMPTY): Rendered = Simplifier(this, ctx).run()

private class Simplifier(scheme: Scheme, private val ctx: TypeCtx) {
    private val root = scheme.type
    private val bounds = scheme.bounds

    private fun lowerOf(v: TypeVar) = bounds.lower(v)
    private fun upperOf(v: TypeVar) = bounds.upper(v)

    private val posOcc = HashSet<TypeVar>()
    private val negOcc = HashSet<TypeVar>()
    private val coPos = HashMap<TypeVar, MutableSet<TypeVar>>()
    private val coNeg = HashMap<TypeVar, MutableSet<TypeVar>>()
    private val walked = HashSet<Pair<TypeVar, Boolean>>()

    // The variables a variable stands for at a polarity: itself and what flows in (positive) or what is
    // demanded of it (negative), through variable edges.
    private fun reach(v: TypeVar, pos: Boolean): Set<TypeVar> {
        val out = LinkedHashSet<TypeVar>()
        val pending = ArrayDeque(listOf(v))
        while (pending.isNotEmpty()) {
            val x = pending.removeFirst()
            if (!out.add(x)) continue
            val next = if (pos) lowerOf(x).let { it.tvs + it.meets.keys } else upperOf(x).tvs
            pending += next
        }
        return out
    }

    private fun walk(t: Type, pos: Boolean) {
        when (t) {
            is TypeVar -> site(t, pos)
            is NullableType -> walk(t.inner, pos)
            is FnType -> { t.paramTypes.forEach { walk(it, !pos) }; walk(t.returnType, pos) }
            is VectorType -> walk(t.el, pos)
            is SetType -> walk(t.el, pos)
            is IterableType -> walk(t.el, pos)
            is IteratorType -> walk(t.el, pos)
            is TagType -> t.args.forEach { walk(it, pos) }
            is EnumType -> t.args.forEach { walk(it, pos) }
            is HostType -> t.args.forEach { walk(it, pos); walk(it, !pos) }
            is Meet -> walk(t.base, pos)
            is PrimType, is RecordType, NothingType -> {}
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
            else upperOf(w).concreteType()?.let { walk(it, false) }
        }
    }

    private val parent = HashMap<TypeVar, TypeVar>()

    private fun find(v: TypeVar): TypeVar {
        var x = v
        while (true) {
            val p = parent[x] ?: return x
            if (p == x) return x
            x = p
        }
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
        for (v in posOcc + negOcc + bounds.keys) {
            for (w in below(v)) if (w != v && v in below(w)) union(v, w)
        }
    }

    private fun unifyCoOccurring() {
        for (v in posOcc intersect negOcc) {
            val both = (coPos[v] ?: emptySet<TypeVar>()) intersect (coNeg[v] ?: emptySet<TypeVar>())
            for (w in both) {
                if (w == v) continue
                // Their concrete bounds must agree, or the merge would invent a type.
                val lc = setOfNotNull(lowerOf(v).concrete, lowerOf(w).concrete)
                val uc = setOfNotNull(upperOf(v).concrete, upperOf(w).concrete)
                if (lc.size <= 1 && uc.size <= 1) union(v, w)
            }
        }
    }

    private val members: Map<TypeVar, List<TypeVar>> by lazy {
        (posOcc + negOcc + bounds.keys).groupBy { find(it) }
    }

    private fun memberList(rep: TypeVar) = members[rep] ?: listOf(rep)

    private fun mergedLower(rep: TypeVar): LowerBound {
        val ms = memberList(rep).toSet()
        val ls = ms.map { lowerOf(it) }
        return LowerBound(
            concrete = ls.firstNotNullOfOrNull { it.concrete },
            nullable = ls.any { it.nullable },
            tvs = ls.flatMap { it.tvs }.map(::find).filter { it != rep }.toSet(),
            meets = ls.flatMap { it.meets.entries }.filter { find(it.key) != rep }
                .groupBy({ find(it.key) }, { it.value }).mapValues { (_, fs) -> fs.reduce { a, b -> a or b } },
        )
    }

    private fun mergedUpper(rep: TypeVar): UpperBound {
        val ms = memberList(rep).toSet()
        val us = ms.map { upperOf(it) }
        return UpperBound(
            concrete = us.firstNotNullOfOrNull { it.concrete },
            nilOk = us.all { it.nilOk },
            tvs = us.flatMap { it.tvs }.map(::find).filter { it != rep }.toSet(),
        )
    }

    private fun occursPos(rep: TypeVar) = memberList(rep).any { it in posOcc }
    private fun occursNeg(rep: TypeVar) = memberList(rep).any { it in negOcc }

    private val resolved = HashMap<TypeVar, Type>()
    private val resolving = HashSet<TypeVar>()

    // What a variable stands for in the rendered type: itself when it carries information, otherwise
    // the one thing it is determined to be. A variable only ever produced is the join of what flows
    // into it, and nothing flowing in makes it Nothing; a variable only ever consumed is its one demand.
    private fun resolve(v: TypeVar): Type {
        val rep = find(v)
        resolved[rep]?.let { return it }
        if (!resolving.add(rep)) return rep
        val pos = occursPos(rep)
        val neg = occursNeg(rep)
        val result: Type = when {
            pos && !neg -> {
                val lo = mergedLower(rep)
                val parts = LinkedHashSet<Type>()
                lo.concreteType()?.let { parts += subst(it, pos = true) }
                lo.tvs.forEach { parts += resolve(it) }
                lo.meets.forEach { (b, f) -> parts += normalise(Meet(resolve(b), f)) }
                parts.removeIf { it is NothingType }
                // (b ∧ F) ∨ b = b
                parts.removeIf { it is Meet && it.base in parts }
                // Bare variables (and meets on them) have no displayable join with anything else.
                val vars = parts.filter { it is TypeVar || (it is Meet && it.base is TypeVar) }
                when {
                    parts.isEmpty() -> NothingType
                    parts.size == 1 -> parts.single()
                    // A variable or nil: the variable, nullable.
                    vars.size == 1 && parts.size == 2 && NothingType.nullable() in parts -> vars.single().nullable()
                    vars.isNotEmpty() -> rep
                    else -> {
                        var acc: Type? = parts.first()
                        for (part in parts.drop(1)) acc = acc?.let { displayJoin(it, part, bounds, ctx, dropVars = false) }
                        acc ?: rep
                    }
                }
            }
            neg && !pos -> {
                val up = mergedUpper(rep)
                val parts = LinkedHashSet<Type>()
                up.concreteType()?.let { parts += it }
                up.tvs.forEach { parts += resolve(it) }
                if (parts.size == 1) parts.single() else rep
            }
            else -> rep
        }
        resolving -= rep
        resolved[rep] = result
        return result
    }

    // Variables the rendered type already shows as nullable, so their nil lower bound is not a constraint.
    private val inlineNullable = HashSet<TypeVar>()

    private fun nullableVars(t: Type, out: MutableSet<TypeVar>) {
        when (t) {
            is NullableType -> { (t.inner as? TypeVar)?.let { out += it }; nullableVars(t.inner, out) }
            is FnType -> { t.paramTypes.forEach { nullableVars(it, out) }; nullableVars(t.returnType, out) }
            is VectorType -> nullableVars(t.el, out)
            is SetType -> nullableVars(t.el, out)
            is IterableType -> nullableVars(t.el, out)
            is IteratorType -> nullableVars(t.el, out)
            is HostType -> t.args.forEach { nullableVars(it, out) }
            is TagType -> t.args.forEach { nullableVars(it, out) }
            is EnumType -> t.args.forEach { nullableVars(it, out) }
            is Meet -> nullableVars(t.base, out)
            is TypeVar, is PrimType, is RecordType, NothingType -> {}
        }
    }

    // pos is true at a positive occurrence, false at a negative one and null where a type argument is
    // invariant. A kept variable that may be nil shows that at its positive occurrences.
    private fun subst(t: Type, pos: Boolean?, depth: Int = 0): Type {
        if (depth > 32) return t
        val r: Type = when (t) {
            is TypeVar -> when (val v = resolve(t)) {
                is TypeVar -> if (pos == true && mergedLower(v).nullable) v.nullable() else v
                else -> subst(v, pos, depth + 1)
            }
            is NullableType -> subst(t.inner, pos, depth).nullable()
            is FnType -> FnType(t.paramTypes.map { subst(it, pos?.not(), depth) }, subst(t.returnType, pos, depth))
            is VectorType -> VectorType(subst(t.el, pos, depth))
            is SetType -> SetType(subst(t.el, pos, depth))
            is IterableType -> IterableType(subst(t.el, pos, depth))
            is IteratorType -> IteratorType(subst(t.el, pos, depth))
            is HostType -> HostType(t.className, t.args.map { subst(it, null, depth) })
            is TagType -> TagType(t.tag, t.args.map { subst(it, pos, depth) })
            is EnumType -> EnumType(t.enum, t.args.map { subst(it, pos, depth) })
            is Meet -> Meet(subst(t.base, pos, depth), t.filter)
            is PrimType, is RecordType, NothingType -> t
        }
        return normalise(r)
    }

    // A meet whose filter the variable's own bounds already imply is just the variable.
    private fun normalise(t: Type): Type = when (t) {
        is Meet -> when (val base = t.base) {
            is TypeVar -> {
                val up = mergedUpper(base)
                val keysImplied = t.filter.record && up.concrete?.let { ctx.recordKeys(it) }?.containsAll(t.filter.keys) == true
                val nilImplied = !t.filter.record && t.filter.notNil && !up.nilOk
                if (keysImplied || nilImplied) base else t
            }
            else -> try { meetOf(base, t.filter, ctx) } catch (_: TypeCheckException) { t }
        }
        is NullableType -> normalise(t.inner).nullable()
        is FnType -> FnType(t.paramTypes.map(::normalise), normalise(t.returnType))
        is VectorType -> VectorType(normalise(t.el))
        is SetType -> SetType(normalise(t.el))
        is IterableType -> IterableType(normalise(t.el))
        is IteratorType -> IteratorType(normalise(t.el))
        is HostType -> HostType(t.className, t.args.map(::normalise))
        is TagType -> TagType(t.tag, t.args.map(::normalise))
        is EnumType -> EnumType(t.enum, t.args.map(::normalise))
        is TypeVar, is PrimType, is RecordType, NothingType -> t
    }

    // A variable that does not appear in the type is not named: its edges are composed away, so a
    // visible variable's constraints mention only concrete types and other visible variables.
    private fun lowersOf(v: TypeVar, visible: Set<TypeVar>, filter: Filter, seen: MutableSet<TypeVar>, out: MutableSet<Type>) {
        if (!seen.add(v)) return
        val lo = mergedLower(v)
        fun emit(t: Type) {
            val s = if (filter.isIdentity) t else normalise(Meet(t, filter))
            if (s !is NothingType && s != v) out += s
        }
        val nilShown = v in inlineNullable
        (if (nilShown) lo.concrete else lo.concreteType())?.let { emit(subst(it, true)) }
        lo.meets.forEach { (b, f) -> edgeLower(b, visible, filter and f, seen, ::emit, out) }
        lo.tvs.forEach { edgeLower(it, visible, filter, seen, ::emit, out) }
    }

    private fun edgeLower(t: TypeVar, visible: Set<TypeVar>, filter: Filter, seen: MutableSet<TypeVar>, emit: (Type) -> Unit, out: MutableSet<Type>) {
        val s = subst(t, true)
        when {
            s is TypeVar && s !in visible -> lowersOf(s, visible, filter, seen, out)
            else -> emit(s)
        }
    }

    private fun uppersOf(v: TypeVar, visible: Set<TypeVar>, seen: MutableSet<TypeVar>, out: MutableSet<Type>) {
        if (!seen.add(v)) return
        val up = mergedUpper(v)
        up.concreteType()?.let { out += subst(it, false) }
        up.tvs.forEach {
            val s = subst(it, false)
            when {
                s is TypeVar && s !in visible -> uppersOf(s, visible, seen, out)
                s != v -> out += s
            }
        }
    }

    // Demands of one kind on one variable, composed through hidden variables, are one demand where
    // their elements can be met for display: a hidden variable with no bounds is anything.
    private fun mergeUppers(ts: Set<Type>, kept: Set<TypeVar>): List<Type> {
        // Nothing is demanded of it, or of anything it flows into.
        fun free(t: Type): Boolean {
            if (t !is TypeVar) return false
            val seen = HashSet<TypeVar>()
            val pending = ArrayDeque(listOf(find(t)))
            while (pending.isNotEmpty()) {
                val x = pending.removeFirst()
                if (!seen.add(x)) continue
                if (x in kept || mergedUpper(x).concrete != null) return false
                pending += mergedUpper(x).tvs.map(::find)
            }
            return true
        }
        fun meet(a: Type, b: Type): Type? = when {
            a == b -> a
            free(a) -> b
            free(b) -> a
            a is VectorType && b is VectorType -> meet(a.el, b.el)?.let { VectorType(it) }
            a is SetType && b is SetType -> meet(a.el, b.el)?.let { SetType(it) }
            a is IterableType && b is IterableType -> meet(a.el, b.el)?.let { IterableType(it) }
            a is RecordType && b is RecordType -> RecordType(a.keys + b.keys)
            else -> null
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
        nullableVars(type, inlineNullable)

        // The variables kept, in order of appearance: those in the type, and those in the constraints
        // it takes to render them. Composing hidden variables away can surface a variable inside a
        // concrete bound, so this closes to a fixpoint.
        val kept = LinkedHashSet<TypeVar>(type.typeVars())
        var constraints: List<Pair<Type, Type>>
        while (true) {
            val found = LinkedHashSet<Pair<Type, Type>>()
            for (v in kept) {
                val lowers = LinkedHashSet<Type>()
                lowersOf(v, kept, Filter(), HashSet(), lowers)
                lowers.forEach { found += it to v }
                val uppers = LinkedHashSet<Type>()
                uppersOf(v, kept, HashSet(), uppers)
                mergeUppers(uppers, kept).forEach { found += v to it }
            }
            constraints = found.toList()
            val more = constraints.flatMap { (l, u) -> l.typeVars() + u.typeVars() }.filter { it !in kept }
            if (more.isEmpty()) break
            kept += more
        }

        // A constraint that only restates a variable's own bound in the other direction is noise.
        val deduped = constraints.filterNot { (l, u) -> l is TypeVar && u is TypeVar && (u to l) in constraints && l.id > u.id }

        val names = kept.withIndex().associate { (i, v) -> v to varName(i) }
        return Rendered(type, deduped, names)
    }
}
