package brj.types

import brj.analyser.*
import brj.runtime.BridjeKey
import brj.runtime.BridjeOptionalKey
import brj.runtime.BridjeTagConstructor
import brj.runtime.BridjeTaggedSingleton
import brj.runtime.BuiltinMetaObj
import brj.runtime.Anomaly

// A typing scheme in Dolan's sense: the positive result type, the negative demand on each free local,
// and the bounds of every variable it mentions. Each occurrence of a local is its own variable and
// each sub-expression its own graph, so the typing of an expression is built from its children's
// alone: their graphs are disjoint and union, and only the constraints the construct itself adds are
// solved. `recurs` carries what `recur` sends each loop binding, until the loop takes it up.
internal class Typing(
    val type: Type,
    val monoEnv: Map<LocalVar, Type> = emptyMap(),
    val bounds: BoundEnv = emptyMap(),
    val recurs: Map<LocalVar, List<Type>> = emptyMap(),
) {
    fun without(locals: Collection<LocalVar>): Typing {
        val bound = locals.toSet()
        return Typing(type, monoEnv - bound, bounds, recurs - bound)
    }
}

internal infix fun Type.subOf(upper: Type): Pair<Type, Type> = this to upper

private fun fail(message: String): Nothing = throw TypeCheckException(message)

internal fun ValueExpr.typing(ctx: TypeCtx = TypeCtx.EMPTY): Typing = Infer(ctx).typing(this)

internal class Infer(private val ctx: TypeCtx) {

    // Composes children: their graphs union, demands several make on one local meet in a fresh variable,
    // and the construct's own constraints are solved in the result.
    private fun build(type: Type, children: Collection<Typing>, constraints: Collection<Pair<Type, Type>> = emptyList()): Typing {
        var bounds: BoundEnv = children.fold(emptyMap()) { acc, c -> acc + c.bounds }
        val meets = mutableListOf<Pair<Type, Type>>()
        val monoEnv = children
            .flatMap { it.monoEnv.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, demands) ->
                if (demands.size == 1) demands.single()
                else TypeVar().also { tv -> demands.forEach { meets += tv subOf it } }
            }
        val recurs = children.flatMap { it.recurs.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.flatten() }
        bounds = bounds.constrainAll(meets + constraints, ctx)
        return Typing(type, monoEnv, bounds, recurs)
    }

    // A global's scheme, freshly instantiated; a global with no known type is anything.
    private fun instantiated(gv: brj.GlobalVar): Typing {
        val scheme = ctx.globalScheme(gv) ?: return Typing(TypeVar())
        val (type, bounds) = scheme.instantiate()
        return Typing(type, bounds = bounds)
    }

    // A fresh instantiation of a tag's type arguments, and the field types its payload gives a
    // constructor call or a pattern in terms of them.
    private class TagInstance(val ctx: TypeCtx, val info: TagInfo, val args: List<Type> = List(info.paramCount) { TypeVar() }) {

        fun fieldTypes(): List<Type> = when (val p = info.payload) {
            Payload.None -> emptyList()
            is Payload.Positional -> p.fields.map { f ->
                when (f) {
                    is FieldType.Param -> args[f.index]
                    FieldType.Untracked -> TypeVar()
                    is FieldType.Fixed -> f.type
                    is FieldType.Key -> ctx.keyType(f.key)
                }
            }
            is Payload.Record -> listOf(RecordType(p.keys))
        }

        val type get() = TagType(info.tag, args)

        // A bare pattern matches the tag and ignores its payload; one with bindings takes all of it.
        fun bind(bindings: List<LocalVar>, body: Typing, constraints: MutableList<Pair<Type, Type>>) {
            if (bindings.isEmpty()) return
            val fieldTypes = fieldTypes()
            if (bindings.size != fieldTypes.size)
                fail("pattern ${info.tag} binds ${fieldTypes.size} values, not ${bindings.size}")
            bindings.zip(fieldTypes).forEach { (lv, ft) -> body.monoEnv[lv]?.let { constraints += ft subOf it } }
        }
    }

    private fun local(lv: LocalVar): Typing = TypeVar().let { Typing(it, mapOf(lv to it)) }

    fun typing(expr: ValueExpr): Typing = when (expr) {
        is IntExpr -> Typing(IntType)
        is DoubleExpr -> Typing(DoubleType)
        is BigIntExpr -> Typing(BigIntType)
        is BigDecExpr -> Typing(BigDecType)
        is StringExpr -> Typing(StrType)
        is BoolExpr -> Typing(BoolType)
        is NilExpr -> Typing(NilType)
        is QuoteExpr -> Typing(FormType)

        is LocalVarExpr -> local(expr.localVar)
        is CapturedVarExpr -> local(expr.outerLocalVar)

        is GlobalVarExpr -> when (val v = expr.globalVar.value) {
            is BridjeKey -> Typing(FnType(listOf(RecordType(setOf(v.sym))), ctx.keyType(v.sym)))
            is BridjeOptionalKey -> Typing(FnType(listOf(RecordType(emptySet())), ctx.keyType(v.key.sym).nullable()))
            is BridjeTagConstructor, is BuiltinMetaObj, is Anomaly.AnomalyMeta ->
                TagInstance(ctx, ctx.tagInfo(v)).let { Typing(FnType(it.fieldTypes(), it.type)) }
            is BridjeTaggedSingleton -> Typing(TagInstance(ctx, ctx.tagInfo(v)).type)
            else -> instantiated(expr.globalVar)
        }

        is EffectVarExpr -> instantiated(expr.effectVar)

        // A foreign-language block is trusted to have its declared type.
        is LangExpr -> Typing(expr.declaredType)

        is DoExpr -> {
            val typings = expr.sideEffects.map { typing(it) } + typing(expr.result)
            build(typings.last().type, typings)
        }

        is IfExpr -> {
            val pred = typing(expr.predExpr)
            val then = typing(expr.thenExpr)
            val els = typing(expr.elseExpr)
            val result = TypeVar()
            build(
                result,
                listOf(pred, then, els),
                listOf(pred.type subOf BoolType, then.type subOf result, els.type subOf result),
            )
        }

        is LetExpr -> {
            val binding = typing(expr.bindingExpr)
            val body = typing(expr.bodyExpr)
            val demand = body.monoEnv[expr.localVar]
            build(
                body.type,
                listOf(binding, body.without(listOf(expr.localVar))),
                listOfNotNull(demand?.let { binding.type subOf it }),
            )
        }

        is FnExpr -> {
            val body = typing(expr.bodyExpr)
            val paramTypes = expr.params.map { body.monoEnv[it] ?: TypeVar() }
            val rest = body.without(expr.params)
            Typing(FnType(paramTypes, body.type), rest.monoEnv, rest.bounds, rest.recurs)
        }

        is CallExpr -> {
            val fn = typing(expr.fnExpr)
            val args = expr.argExprs.map { typing(it) }
            val result = TypeVar()
            build(result, listOf(fn) + args, listOf(fn.type subOf FnType(args.map { it.type }, result)))
        }

        is VectorExpr -> {
            val elTypings = expr.els.map { typing(it) }
            val el = TypeVar()
            build(VectorType(el), elTypings, elTypings.map { it.type subOf el })
        }

        is SetExpr -> {
            val elTypings = expr.els.map { typing(it) }
            val el = TypeVar()
            build(SetType(el), elTypings, elTypings.map { it.type subOf el })
        }

        is RecordExpr -> {
            val valueTypings = expr.fields.map { (_, v) -> typing(v) }
            build(
                RecordType(expr.fields.map { it.first }.toSet()),
                valueTypings,
                expr.fields.zip(valueTypings).map { (field, t) -> t.type subOf ctx.keyType(field.first) },
            )
        }

        is RecordUpdateExpr -> {
            val record = typing(expr.recordExpr)
            val valueTypings = expr.fields.map { (_, v) -> typing(v) }
            build(
                meetOf(record.type, Filter.keys(expr.fields.map { it.first }.toSet()), ctx),
                listOf(record) + valueTypings,
                listOf(record.type subOf RecordType(emptySet())) +
                    expr.fields.zip(valueTypings).map { (field, t) -> t.type subOf ctx.keyType(field.first) },
            )
        }

        // set(r, .k, v): mutates in place and yields the old value, or nil. r's static type is unchanged.
        is RecordSetExpr -> {
            val record = typing(expr.recordExpr)
            val value = typing(expr.valueExpr)
            build(
                ctx.keyType(expr.key).nullable(),
                listOf(record, value),
                listOf(record.type subOf RecordType(emptySet()), value.type subOf ctx.keyType(expr.key)),
            )
        }

        is CaseExpr -> caseTyping(expr)

        is TryCatchExpr -> {
            val body = typing(expr.bodyExpr)
            val result = TypeVar()
            val children = mutableListOf(body)
            val constraints = mutableListOf(body.type subOf result)
            for (branch in expr.catchBranches) {
                val handler = typing(branch.bodyExpr)
                when (val p = branch.pattern) {
                    is TagPattern -> {
                        TagInstance(ctx, ctx.tagInfo(p.tagValue)).bind(p.bindings, handler, constraints)
                        children += handler.without(p.bindings)
                    }
                    is CatchAllBindingPattern -> children += handler.without(listOf(p.binding))
                    is DefaultPattern, is NilPattern -> children += handler
                }
                constraints += handler.type subOf result
            }
            expr.finallyExpr?.let { children += typing(it) }
            build(result, children, constraints)
        }

        is LoopExpr -> {
            val inits = expr.bindings.map { (_, e) -> typing(e) }
            val body = typing(expr.bodyExpr)
            val locals = expr.bindings.map { it.first }
            val constraints = expr.bindings.zip(inits).flatMap { (binding, init) ->
                val lv = binding.first
                val demand = body.monoEnv[lv] ?: return@flatMap emptyList()
                listOf(init.type subOf demand) + body.recurs[lv].orEmpty().map { it subOf demand }
            }
            build(body.type, inits + body.without(locals), constraints)
        }

        // recur never returns; its arguments flow into the loop bindings.
        is RecurExpr -> {
            val args = expr.argExprs.map { typing(it) }
            val flows = expr.bindings.zip(args).associate { (lv, arg) -> lv to listOf(arg.type) }
            build(BottomType, args).let { Typing(it.type, it.monoEnv, it.bounds, it.recurs + flows) }
        }

        is WithFxExpr -> {
            val bound = expr.bindings.map { (_, e) -> typing(e) }
            val effects = expr.bindings.map { (gv, _) -> instantiated(gv) }
            val body = typing(expr.bodyExpr)
            build(body.type, bound + effects + body, bound.zip(effects).map { (value, effect) -> value.type subOf effect.type })
        }

        // Undeclared host members and raw host objects: the checker knows nothing about them.
        is HostStaticMethodExpr, is HostConstructorExpr, is TruffleObjectExpr -> Typing(TypeVar())

        is ErrorValueExpr -> Typing(TypeVar())
    }

    // case: the tag patterns instantiate one nominal family, and the scrutinee is demanded to be it.
    // Without a default the handled tags must cover it. A catch-all binding sees the scrutinee narrowed:
    // to the one remaining variant when exactly one remains, and past nil when nil has its own branch.
    // Standalone tags have no enum to be the sum of: two of them need a default, which then handles
    // whatever else the scrutinee may be, and the case demands nothing of it.
    private fun caseTyping(expr: CaseExpr): Typing {
        val scrutineeTyping = typing(expr.scrutinee)
        val result = TypeVar()
        val children = mutableListOf(scrutineeTyping)
        val constraints = mutableListOf<Pair<Type, Type>>()

        var family: TagInstance? = null
        val handled = LinkedHashSet<TagRef>()
        var hasNil = false
        var hasDefault = false

        val hasDefaultAnywhere = expr.branches.any { it.pattern is DefaultPattern || it.pattern is CatchAllBindingPattern }
        var mixedStandalone = false

        // Every tag pattern describes the one scrutinee, so patterns from one family share its arguments.
        fun instance(info: TagInfo): TagInstance {
            family?.let { f ->
                val sameFamily = if (f.info.enum != null) f.info.enum == info.enum else f.info.tag == info.tag
                if (sameFamily) return TagInstance(ctx, info, f.args)
                if (f.info.enum == null && info.enum == null && hasDefaultAnywhere) {
                    mixedStandalone = true
                    return TagInstance(ctx, info)
                }
                fail("case patterns ${f.info.tag} and ${info.tag} belong to different enums")
            }
            return TagInstance(ctx, info).also { family = it }
        }

        fun remainingVariants(): List<TagRef>? =
            family?.info?.enum?.let { e -> ctx.variants(e).map { it.tag } - handled }

        fun narrowed(): Type {
            val remaining = remainingVariants()
            return when {
                remaining != null && remaining.size == 1 -> TagType(remaining.single(), family!!.args)
                hasNil -> meetOf(scrutineeTyping.type, Filter.NOT_NIL, ctx)
                else -> scrutineeTyping.type
            }
        }

        for (branch in expr.branches) {
            val body = typing(branch.bodyExpr)
            when (val p = branch.pattern) {
                is TagPattern -> {
                    val inst = instance(ctx.tagInfo(p.tagValue))
                    handled += inst.info.tag
                    inst.bind(p.bindings, body, constraints)
                    children += body.without(p.bindings)
                }
                is NilPattern -> {
                    hasNil = true
                    children += body
                }
                is DefaultPattern -> {
                    hasDefault = true
                    children += body
                }
                is CatchAllBindingPattern -> {
                    hasDefault = true
                    body.monoEnv[p.binding]?.let { constraints += narrowed() subOf it }
                    children += body.without(listOf(p.binding))
                }
            }
            constraints += body.type subOf result
        }

        val demand: Type? = when {
            mixedStandalone -> null
            family != null && family!!.info.enum == null && hasDefault -> null
            family != null -> family!!.info.enum?.let { EnumType(it, family!!.args) } ?: family!!.type
            hasDefault -> null
            // Only nil was handled, with nothing to catch the rest: the scrutinee had better be nil.
            else -> BottomType
        }
        demand?.let { constraints += scrutineeTyping.type subOf (if (hasNil) it.nullable() else it) }

        if (!hasDefault) {
            val missing = remainingVariants().orEmpty()
            if (missing.isNotEmpty()) fail("non-exhaustive case: missing ${missing.joinToString(", ")}")
        }

        return build(result, children, constraints)
    }
}
