package brj.types

// The type as a consumer of the value sees it: variables read through their lower bounds.
fun Type.positive(env: BoundEnv, ctx: TypeCtx = TypeCtx.EMPTY): Type = positive(env, ctx, emptySet())

private fun Type.positive(env: BoundEnv, ctx: TypeCtx, seen: Set<TypeVar>): Type = when (this) {
    is TypeVar -> {
        if (this in seen) this
        else {
            val lower = env.lower(this)
            // What flowed in through variables has already flowed into the concrete disjuncts, so the
            // variables themselves are only consulted when there is nothing else.
            val parts = lower.disjuncts().map { it.positive(env, ctx, seen + this) }.ifEmpty {
                lower.tvs.map { it.positive(env, ctx, seen + this) }.filter { it !is TypeVar }
            }
            when (parts.size) {
                0 -> this
                1 -> parts.single()
                else -> parts.reduce { a, b -> displayJoin(a, b, env, ctx) ?: return this }
            }
        }
    }
    is Meet -> when (val pb = base.positive(env, ctx, seen)) {
        is TypeVar -> Meet(pb, filter)
        else -> try { meetOf(pb, filter, ctx) } catch (_: TypeCheckException) { Meet(pb, filter) }
    }
    is NullableType -> inner.positive(env, ctx, seen).nullable()
    is FnType -> FnType(paramTypes.map { it.negative(env, ctx) }, returnType.positive(env, ctx, seen))
    is VectorType -> VectorType(el.positive(env, ctx, seen))
    is SetType -> SetType(el.positive(env, ctx, seen))
    is TagType -> TagType(tag, args.map { it.positive(env, ctx, seen) })
    is EnumType -> EnumType(enum, args.map { it.positive(env, ctx, seen) })
    is HostType -> HostType(className, args.map { it.positive(env, ctx, seen) })
    is IterableType -> IterableType(el.positive(env, ctx, seen))
    is IteratorType -> IteratorType(el.positive(env, ctx, seen))
    is PrimType, is RecordType, BottomType, NilType -> this
}

// The join of two displayed types, or null where none is displayable.
internal fun displayJoin(a: Type, b: Type, env: BoundEnv, ctx: TypeCtx, dropVars: Boolean = true): Type? {
    if (a == b) return a
    if (a is BottomType) return b
    if (b is BottomType) return a
    if (a is NilType) return b.nullable()
    if (b is NilType) return a.nullable()
    if (a is NullableType || b is NullableType) {
        val inner = displayJoin((a as? NullableType)?.inner ?: a, (b as? NullableType)?.inner ?: b, env, ctx, dropVars) ?: return null
        return inner.nullable()
    }
    // An unresolved variable adds nothing displayable.
    if (a is TypeVar) return if (dropVars) b else null
    if (b is TypeVar) return if (dropVars) a else null

    val (ba, _) = if (a is Meet) a.base to a.filter else a to Filter()
    val (bb, _) = if (b is Meet) b.base to b.filter else b to Filter()
    if (ctx.isNominal(ba) && ctx.isNominal(bb)) return nominalDisplayJoin(a, b, env, ctx, dropVars)

    val ka = ctx.recordKeys(a)
    val kb = ctx.recordKeys(b)
    if (ka != null && kb != null) return RecordType(ka intersect kb)

    return when {
        a is VectorType && b is VectorType -> displayJoin(a.el, b.el, env, ctx, dropVars)?.let { VectorType(it) }
        a is SetType && b is SetType -> displayJoin(a.el, b.el, env, ctx, dropVars)?.let { SetType(it) }
        a is IterableType && b is IterableType -> displayJoin(a.el, b.el, env, ctx, dropVars)?.let { IterableType(it) }
        a is HostType && b is HostType && a.className == b.className && a.args.size == b.args.size ->
            HostType(a.className, a.args.zip(b.args).map { (x, y) -> displayJoin(x, y, env, ctx, dropVars) ?: return null })
        else -> null
    }
}

private fun nominalDisplayJoin(a: Type, b: Type, env: BoundEnv, ctx: TypeCtx, dropVars: Boolean): Type? {
    val (ba, fa) = if (a is Meet) a.base to a.filter else a to Filter()
    val (bb, fb) = if (b is Meet) b.base to b.filter else b to Filter()

    fun args(x: Type) = when (x) { is TagType -> x.args; is EnumType -> x.args; else -> emptyList() }
    val joinedArgs = args(ba).zip(args(bb)).map { (x, y) -> displayJoin(x, y, env, ctx, dropVars) ?: return null }

    val joined: Type = when {
        ba is TagType && bb is TagType && ba.tag == bb.tag -> TagType(ba.tag, joinedArgs)
        ba is TagType && bb is TagType -> {
            val e = ctx.tagInfo(ba.tag).enum
            if (e != null && e == ctx.tagInfo(bb.tag).enum) EnumType(e, joinedArgs) else return null
        }
        ba is TagType && bb is EnumType -> if (ctx.tagInfo(ba.tag).enum == bb.enum) EnumType(bb.enum, joinedArgs) else return null
        ba is EnumType && bb is TagType -> if (ctx.tagInfo(bb.tag).enum == ba.enum) EnumType(ba.enum, joinedArgs) else return null
        ba is EnumType && bb is EnumType && ba.enum == bb.enum -> EnumType(ba.enum, joinedArgs)
        else -> return null
    }
    return try { meetOf(joined, fa or fb, ctx) } catch (_: TypeCheckException) { null }
}

// The type as a producer of the value sees it: variables read through their upper bounds.
fun Type.negative(env: BoundEnv, ctx: TypeCtx = TypeCtx.EMPTY): Type = when (this) {
    is TypeVar -> env.upper(this).concreteType()?.negative(env, ctx) ?: this
    is NullableType -> inner.negative(env, ctx).nullable()
    is FnType -> FnType(paramTypes.map { it.positive(env, ctx) }, returnType.negative(env, ctx))
    is VectorType -> VectorType(el.negative(env, ctx))
    is SetType -> SetType(el.negative(env, ctx))
    is TagType -> TagType(tag, args.map { it.negative(env, ctx) })
    is EnumType -> EnumType(enum, args.map { it.negative(env, ctx) })
    is HostType -> HostType(className, args.map { it.negative(env, ctx) })
    is IterableType -> IterableType(el.negative(env, ctx))
    is IteratorType -> IteratorType(el.negative(env, ctx))
    is PrimType, is RecordType, is Meet, BottomType, NilType -> this
}
