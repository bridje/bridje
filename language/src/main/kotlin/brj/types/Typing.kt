package brj.types

import brj.analyser.*
import brj.runtime.BridjeKey
import brj.runtime.BridjeOptionalKey
import brj.runtime.BridjeTagConstructor
import brj.runtime.BridjeTaggedSingleton
import brj.runtime.BuiltinMetaObj
import brj.runtime.Anomaly
import brj.runtime.QSymbol
import brj.runtime.TagConstructor

// A typing scheme in Dolan's sense: the positive result type, the negative demand on each free local,
// and the bounds of every variable it mentions. Each occurrence of a local is its own variable and
// each sub-expression its own graph, so the typing of an expression is built from its children's
// alone: their graphs are disjoint and union, and only the constraints the construct itself adds are
// solved. `recurs` carries what `recur` sends each binding of a loop or a fn, until that binder takes it up.
internal class Typing(
    val type: Type,
    val monoEnv: Map<LocalVar, Type> = emptyMap(),
    val bounds: BoundEnv = emptyMap(),
    val recurs: Map<LocalVar, List<Type>> = emptyMap(),
) {
    // Takes locals out of scope. A recur target's flows belong to its binder, which takes them with `recurInto`.
    fun without(locals: Collection<LocalVar>): Typing {
        val bound = locals.toSet()
        check(recurs.keys.none { it in bound }) { "recur flows into ${recurs.keys intersect bound} would be dropped" }
        return Typing(type, monoEnv - bound, bounds, recurs)
    }

    // A loop's or a fn's bindings out of scope, each binding's demand, and what `recur` sends each flowing into it.
    fun recurInto(bindings: List<LocalVar>): Triple<Typing, List<Type?>, List<Pair<Type, Type>>> {
        val demands = bindings.map { monoEnv[it] }
        val flows = bindings.zip(demands).flatMap { (lv, demand) -> demand?.let { d -> recurs[lv].orEmpty().map { it subOf d } }.orEmpty() }
        val bound = bindings.toSet()
        return Triple(Typing(type, monoEnv - bound, bounds, recurs - bound), demands, flows)
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
                else freshVar().also { tv -> demands.forEach { meets += tv subOf it } }
            }
        val recurs = children.flatMap { it.recurs.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.flatten() }
        bounds = bounds.constrainAll(meets + constraints, ctx)
        return Typing(type, monoEnv, bounds, recurs)
    }

    // A global's scheme, freshly instantiated; a global with no known type is anything.
    private fun instantiated(gv: brj.GlobalVar): Typing {
        val scheme = ctx.globalScheme(gv) ?: return Typing(freshVar())
        val (type, bounds) = scheme.instantiate()
        return Typing(type, bounds = bounds)
    }

    // A fresh instantiation of a tag's type arguments, and the types its payload gives a constructor call
    // or a pattern in terms of them.
    private class TagInstance(val ctx: TypeCtx, val info: TagInfo, val args: List<Type> = List(info.paramCount) { freshVar() }) {

        private val record = info.payload as? Payload.Record

        // The instance a key the tag declares is at, in terms of these arguments.
        fun instance(key: QSymbol): List<Type> {
            val at = info.params.zip(args).toMap()
            return record!!.template(key).map { it.substitute(at) }
        }

        fun keyType(key: QSymbol): Type = ctx.keyTypeAt(key, instance(key))

        // The tag's record: its keys, each at this instance.
        private fun ownRecord(): Type = RecType(null, emptyList(), record!!.keys.associateWith { Slot(true, instance(it)) })

        fun argTypes(): List<Type> = when (val p = info.payload) {
            Payload.None -> emptyList()
            is Payload.Record -> listOf(ownRecord())
            is Payload.Opaque -> List(p.arity) { freshVar() }
        }

        // The tag at these arguments. Built from a record it may carry more keys than its own, so it is open;
        // a tag with no record is exactly its name.
        val type get() = RecType(info.tag, args, emptyMap(), closed = record == null)

        // A bare pattern matches the tag and ignores its payload. A declared tag's value is its record, so
        // `Just(r)` binds the tagged value itself; a builtin meta's binds the record inside it.
        fun bind(pattern: TagPattern, body: Typing, constraints: MutableList<Pair<Type, Type>>) {
            when (val payload = pattern.payload) {
                null -> {}
                is PayloadBinding.Whole -> body.monoEnv[payload.binding]?.let {
                    val bound = if (pattern.tagValue is BridjeTagConstructor) type else ownRecord()
                    constraints += bound subOf it
                }
                // Only the tag's own keys: nothing says another is there, as neither a `catch` nor a `case` over
                // several variants has one value's type to demand it of. Another key is read with `.?k`.
                is PayloadBinding.Keys -> payload.bindings.forEach { (key, lv) ->
                    if (record == null || key.sym !in record.keys)
                        fail("${info.tag} does not declare ${key.sym.toDisplayString()}: read it with .?${key.sym.name.name}")
                    body.monoEnv[lv]?.let { constraints += keyType(key.sym) subOf it }
                }
            }
        }
    }

    private val PayloadBinding?.locals: List<LocalVar>
        get() = when (this) {
            null -> emptyList()
            is PayloadBinding.Whole -> listOf(binding)
            is PayloadBinding.Keys -> bindings.map { it.second }
        }

    // A tag constructed from a record literal: each of the tag's keys holds its type at this instance, and the
    // tag carries the literal's other keys too, at instances of their own, and nothing else.
    private fun constructed(expr: CallExpr): Typing? {
        val ctor = (expr.fnExpr as? GlobalVarExpr)?.globalVar?.value ?: return null
        if (ctor !is TagConstructor) return null
        val fields = (expr.argExprs.singleOrNull() as? RecordExpr)?.fields ?: return null
        val inst = TagInstance(ctx, ctx.tagInfo(ctor))
        val keys = (inst.info.payload as? Payload.Record)?.keys ?: return null
        val given = fields.map { it.first }.toSet()
        (keys - given).takeIf { it.isNotEmpty() }?.let { fail("${RecordType(given)} lacks ${RecordType(it)}") }
        val extras = (given - keys).associateWith { Slot(true, ctx.freshInstance(it)) }
        val values = fields.map { (_, e) -> typing(e) }
        return build(
            RecType(inst.info.tag, inst.args, extras, closed = true),
            values,
            fields.zip(values).map { (f, v) ->
                val k = f.first
                v.type subOf (extras[k]?.let { ctx.keyTypeAt(k, it.args) } ?: inst.keyType(k))
            },
        )
    }

    // Each key at an instance of its own, and what its value must be for it.
    private fun freshSlots(keys: List<QSymbol>): Map<QSymbol, Slot> = keys.associateWith { Slot(true, ctx.freshInstance(it)) }

    private fun local(lv: LocalVar): Typing = freshVar().let { Typing(it, mapOf(lv to it)) }

    fun typing(expr: ValueExpr): Typing = when (expr) {
        is IntExpr -> Typing(IntType)
        is DoubleExpr -> Typing(DoubleType)
        is BigIntExpr -> Typing(BigIntType)
        is BigDecExpr -> Typing(BigDecType)
        is StringExpr -> Typing(StrType)
        is BoolExpr -> Typing(BoolType)
        is NilExpr -> Typing(NothingType.nullable())
        is QuoteExpr -> Typing(FormType)

        is LocalVarExpr -> local(expr.localVar)
        is CapturedVarExpr -> local(expr.outerLocalVar)

        is GlobalVarExpr -> when (val v = expr.globalVar.value) {
            // A key reads its value at the instance the record holds it at; `.?k` reads one the record may lack.
            is BridjeKey -> ctx.freshInstance(v.sym).let { inst ->
                Typing(FnType(listOf(RecType(null, emptyList(), mapOf(v.sym to Slot(true, inst)))), ctx.keyTypeAt(v.sym, inst)))
            }
            is BridjeOptionalKey -> ctx.freshInstance(v.key.sym).let { inst ->
                Typing(FnType(listOf(RecType(null, emptyList(), mapOf(v.key.sym to Slot(false, inst)))), ctx.keyTypeAt(v.key.sym, inst).nullable()))
            }
            is BridjeTagConstructor, is BuiltinMetaObj, is Anomaly.AnomalyMeta ->
                TagInstance(ctx, ctx.tagInfo(v)).let { Typing(FnType(it.argTypes(), it.type)) }
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
            val result = freshVar()
            build(
                result,
                listOf(pred, then, els),
                listOf(pred.type subOf BoolType, then.type subOf result, els.type subOf result),
            )
        }

        is IfLetExpr -> {
            val value = typing(expr.valueExpr)
            val then = typing(expr.thenExpr)
            val els = typing(expr.elseExpr)
            val result = freshVar()
            build(
                result,
                listOf(value, then.without(listOf(expr.localVar)), els),
                listOfNotNull(then.monoEnv[expr.localVar]?.let { value.type subOf it.nullable() }) +
                    listOf(then.type subOf result, els.type subOf result),
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

        // A fn is a recur target: its recur's arguments flow into its parameters.
        is FnExpr -> {
            val (rest, demands, flows) = typing(expr.bodyExpr).recurInto(expr.params)
            build(FnType(demands.map { it ?: freshVar() }, rest.type), listOf(rest), flows)
        }

        is CallExpr -> constructed(expr) ?: run {
            val fn = typing(expr.fnExpr)
            val args = expr.argExprs.map { typing(it) }
            val result = freshVar()
            build(result, listOf(fn) + args, listOf(fn.type subOf FnType(args.map { it.type }, result)))
        }

        is VectorExpr -> {
            val elTypings = expr.els.map { typing(it) }
            val el = freshVar()
            build(VectorType(el), elTypings, elTypings.map { it.type subOf el })
        }

        is SetExpr -> {
            val elTypings = expr.els.map { typing(it) }
            val el = freshVar()
            build(SetType(el), elTypings, elTypings.map { it.type subOf el })
        }

        // A literal carries exactly its keys, so it is closed.
        is RecordExpr -> {
            val slots = freshSlots(expr.fields.map { it.first })
            val valueTypings = expr.fields.map { (_, v) -> typing(v) }
            build(
                RecType(null, emptyList(), slots, closed = true),
                valueTypings,
                expr.fields.zip(valueTypings).map { (field, t) -> t.type subOf ctx.keyTypeAt(field.first, slots.getValue(field.first).args) },
            )
        }

        is RecordUpdateExpr -> {
            val record = typing(expr.recordExpr)
            val slots = freshSlots(expr.fields.map { it.first })
            val valueTypings = expr.fields.map { (_, v) -> typing(v) }
            build(
                withKeys(record.type, slots, ctx),
                listOf(record) + valueTypings,
                listOf(record.type subOf AnyRecord) +
                    expr.fields.zip(valueTypings).map { (field, t) -> t.type subOf ctx.keyTypeAt(field.first, slots.getValue(field.first).args) },
            )
        }

        is CaseExpr -> caseTyping(expr)

        is TryCatchExpr -> {
            val body = typing(expr.bodyExpr)
            val result = freshVar()
            val children = mutableListOf(body)
            val constraints = mutableListOf(body.type subOf result)
            for (branch in expr.catchBranches) {
                val handler = typing(branch.bodyExpr)
                when (val p = branch.pattern) {
                    is TagPattern -> {
                        TagInstance(ctx, ctx.tagInfo(p.tagValue)).bind(p, handler, constraints)
                        children += handler.without(p.payload.locals)
                    }
                    // Whatever is caught is an anomaly: `throw` takes one, and a host exception is caught as `Host`.
                    is CatchAllBindingPattern -> {
                        handler.monoEnv[p.binding]?.let { constraints += AnomalyType subOf it }
                        children += handler.without(listOf(p.binding))
                    }
                    is DefaultPattern -> children += handler
                }
                constraints += handler.type subOf result
            }
            expr.finallyExpr?.let { children += typing(it) }
            build(result, children, constraints)
        }

        is LoopExpr -> {
            val inits = expr.bindings.map { (_, e) -> typing(e) }
            val (rest, demands, flows) = typing(expr.bodyExpr).recurInto(expr.bindings.map { it.first })
            val initFlows = inits.zip(demands).mapNotNull { (init, demand) -> demand?.let { init.type subOf it } }
            build(rest.type, inits + rest, initFlows + flows)
        }

        // recur never returns; its arguments flow into the loop bindings.
        is RecurExpr -> {
            val args = expr.argExprs.map { typing(it) }
            val flows = expr.bindings.zip(args).associate { (lv, arg) -> lv to listOf(arg.type) }
            build(NothingType, args).let { Typing(it.type, it.monoEnv, it.bounds, it.recurs + flows) }
        }

        // A handler serves every call in the body, each at whatever instance of the effect's declared type it
        // takes, so it is checked against that type with its variables held rigid, as a definition is.
        is WithFxExpr -> {
            val handlers = expr.bindings.map { (_, e) -> typing(e) }
            val body = typing(expr.bodyExpr)
            val built = build(body.type, handlers + body)
            val bounds = expr.bindings.zip(handlers).fold(built.bounds) { env, (binding, handler) ->
                val effect = binding.first
                val declared = ctx.globalScheme(effect) ?: return@fold env
                check(declared.bounds.isEmpty()) { "an effect's declared type has no bounds: ${effect.name}" }
                env.constrainRigidly(handler.type, declared.type, ctx) {
                    "the handler for ${effect.name} is less general than its declared type, ${declared.simplify(ctx)}"
                }
            }
            Typing(built.type, built.monoEnv, bounds, built.recurs)
        }

        // Undeclared host members and raw host objects: the checker knows nothing about them.
        is HostStaticMethodExpr, is HostConstructorExpr, is TruffleObjectExpr -> Typing(freshVar())

        is ErrorValueExpr -> Typing(freshVar())
    }

    // case: the tag patterns instantiate one nominal family, and the scrutinee is demanded to be it.
    // Without a default the handled tags must cover it. A catch-all binding sees the scrutinee narrowed:
    // to the one remaining variant when exactly one remains.
    // Standalone tags have no enum to be the sum of: two of them need a default, which then handles
    // whatever else the scrutinee may be, and the case demands nothing of it.
    private fun caseTyping(expr: CaseExpr): Typing {
        val scrutineeTyping = typing(expr.scrutinee)
        val result = freshVar()
        val children = mutableListOf(scrutineeTyping)
        val constraints = mutableListOf<Pair<Type, Type>>()

        var family: TagInstance? = null
        val handled = LinkedHashSet<TagRef>()
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

        fun narrowed(): Type =
            remainingVariants()?.singleOrNull()?.let { TagType(it, family!!.args) } ?: scrutineeTyping.type

        for (branch in expr.branches) {
            val body = typing(branch.bodyExpr)
            when (val p = branch.pattern) {
                is TagPattern -> {
                    val inst = instance(ctx.tagInfo(p.tagValue))
                    handled += inst.info.tag
                    inst.bind(p, body, constraints)
                    children += body.without(p.payload.locals)
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

        val demand: Type? = family?.let { f ->
            when {
                mixedStandalone -> null
                f.info.enum == null && hasDefault -> null
                else -> f.info.enum?.let { EnumType(it, f.args) } ?: f.type
            }
        }
        demand?.let { constraints += scrutineeTyping.type subOf it }

        if (!hasDefault) {
            val missing = remainingVariants().orEmpty()
            if (missing.isNotEmpty()) fail("non-exhaustive case: missing ${missing.joinToString(", ")}")
        }

        return build(result, children, constraints)
    }
}
