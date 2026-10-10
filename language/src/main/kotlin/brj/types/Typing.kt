package brj.types

import brj.analyser.*
import brj.runtime.BridjeKey
import brj.runtime.BridjeOptionalKey
import brj.runtime.QSymbol

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
    fun without(locals: Collection<LocalVar>): Typing {
        val bound = locals.toSet()
        check(recurs.keys.none { it in bound }) { "recur flows into ${recurs.keys intersect bound} would be dropped" }
        return Typing(type, monoEnv - bound, bounds, recurs)
    }

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

    private fun instantiated(gv: brj.GlobalVar): Typing {
        val scheme = ctx.globalScheme(gv) ?: return Typing(freshVar())
        val (type, bounds) = scheme.instantiate()
        return Typing(type, bounds = bounds)
    }

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
            is BridjeKey -> ctx.freshInstance(v.sym).let { inst ->
                Typing(FnType(listOf(RecType(null, emptyList(), mapOf(v.sym to Slot(true, inst)))), ctx.keyTypeAt(v.sym, inst)))
            }
            is BridjeOptionalKey -> ctx.freshInstance(v.key.sym).let { inst ->
                Typing(FnType(listOf(RecType(null, emptyList(), mapOf(v.key.sym to Slot(false, inst)))), ctx.keyTypeAt(v.key.sym, inst).nullable()))
            }
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

        is IfLetExpr -> Typing(freshVar())

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
            val (rest, demands, flows) = typing(expr.bodyExpr).recurInto(expr.params)
            build(FnType(demands.map { it ?: freshVar() }, rest.type), listOf(rest), flows)
        }

        is CallExpr -> run {
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

        is CaseExpr, is TryCatchExpr, is LoopExpr, is RecurExpr, is WithFxExpr -> Typing(freshVar())

        // Undeclared host members and raw host objects: the checker knows nothing about them.
        is HostStaticMethodExpr, is HostConstructorExpr, is TruffleObjectExpr -> Typing(freshVar())

        is ErrorValueExpr -> Typing(freshVar())
    }
}
