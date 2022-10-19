package brj

import brj.Polarity.NEG
import brj.Polarity.POS
import brj.SimpleType.*
import brj.SimpleType.Function
import brj.SimpleType.Number
import brj.Term.*
import brj.Type.*
import brj.Type.RecordType

sealed class Term {
    data class Num(val value: Int) : Term()
    data class Var(val name: String) : Term()
    data class Lam(val name: String, val rhs: Term) : Term()
    data class App(val lhs: Term, val rhs: Term) : Term()
    data class Rcd(val fields: Map<String, Term>) : Term()
    data class Sel(val receiver: Term, val fieldName: String) : Term()
}

sealed class Type {
    object Top : Type() {
        override fun toString() = "Top"
    }

    object Bot : Type() {
        override fun toString() = "Bot"
    }

    data class Union(val ts: Set<Type>) : Type()
    data class Intersection(val ts: Set<Type>) : Type()

    data class FunctionType(val lhs: Type, val rhs: Type) : Type()
    data class RecordType(val fields: Map<String, Type>) : Type()

    data class TypeVariable(val name: String) : Type() {
        override fun toString() = "(TVar $name)"
    }

    object NumberType : Type() {
        override fun toString() = "Number"
    }

    object BoolType : Type() {
        override fun toString() = "Bool"
    }
}

private fun err(msg: String): Nothing = throw RuntimeException("type error: '$msg'")

private sealed class SimpleType {
    sealed interface Primitive

    open fun walk(outer: SimpleType.() -> SimpleType, inner: SimpleType.() -> SimpleType): SimpleType = outer(this)

    fun postWalk(f: SimpleType.() -> SimpleType): SimpleType = walk(f) { postWalk(f) }

    data class Variable(
        val uniqueName: String,
        val lvl: Int,
        val st: VariableState
    ) : SimpleType(), CompactTypeOrVariable {

        override fun toString() = "$uniqueName[lower: ${st.lowerBounds}; upper: ${st.upperBounds}]"
    }

    object Number : SimpleType(), Primitive {
        override fun toString() = "Num"
    }

    object Bool : SimpleType(), Primitive {
        override fun toString() = "Bool"
    }

    data class Function(val lhs: SimpleType, val rhs: SimpleType) : SimpleType() {
        override fun walk(outer: (SimpleType) -> SimpleType, inner: (SimpleType) -> SimpleType) =
            outer(Function(inner(lhs), inner(rhs)))
    }

    data class Record(val fields: Map<String, SimpleType>) : SimpleType() {
        override fun walk(outer: (SimpleType) -> SimpleType, inner: (SimpleType) -> SimpleType) =
            outer(Record(fields.mapValues { inner(it.value) }))
    }
}

private class VariableState(
    var lowerBounds: List<SimpleType> = emptyList(),
    var upperBounds: List<SimpleType> = emptyList()
) {
    override fun toString(): String {
        return "(VarSt: lower = $lowerBounds; upper = $upperBounds)"
    }
}

private enum class Polarity {
    POS {
        override val mzero = Bot
        override fun mplus(ts: Set<Type>) = Union(ts)
    },
    NEG {
        override val mzero = Top
        override fun mplus(ts: Set<Type>) = Intersection(ts)
    };

    abstract val mzero: Type
    abstract fun mplus(ts: Set<Type>): Type

    operator fun not() = when (this) {
        POS -> NEG; NEG -> POS
    }
}

private data class CompactFunction(val lhs: CompactType, val rhs: CompactType)
private data class CompactRecord(val fields: Map<String, CompactType>)

private data class CompactType(
    val vars: Set<Variable> = emptySet(),
    val prims: Set<Primitive> = emptySet(),
    val rcd: CompactRecord? = null,
    val fn: CompactFunction? = null
) : CompactTypeOrVariable

private data class CompactTypeScheme(val type: CompactType, val recVars: Map<Variable, CompactType>)

private data class PolarVariable(val vs: VariableState, val p: Polarity)

private fun Iterable<CompactType>.merge(p: Polarity): CompactType =
    CompactType(
        vars = flatMap { it.vars }.toSet(),
        prims = flatMap { it.prims }.toSet(),
        fn = mapNotNull { it.fn }
            .takeIf { it.isNotEmpty() }
            ?.let { fns ->
                CompactFunction(
                    fns.map { it.lhs }.merge(!p),
                    fns.map { it.rhs }.merge(p)
                )
            },
        rcd = mapNotNull { it.rcd }
            .takeIf { it.isNotEmpty() }
            ?.let { rcds ->
                when (p) {
                    POS -> {
                        rcds
                            .map { it.fields.keys }
                            .reduce { l, r -> l.intersect(r) }
                            .associateWith { k -> rcds.map { it.fields.getValue(k) }.merge(p) }
                    }

                    NEG -> {
                        rcds
                            .flatMap { it.fields.asSequence() }
                            .groupBy({ it.key }, { it.value })
                            .mapValues { it.value.merge(p) }
                    }
                }
            }
            ?.let { CompactRecord(it) }
    )


private sealed interface CompactTypeOrVariable

private class Typer {

    private var varCount = 0
    fun freshVar(lvl: Int, st: VariableState = VariableState()) = Variable("tv${varCount++}", lvl, st)

    fun SimpleType.instantiateAbove(lvl: Int): SimpleType {
        val tvs = mutableMapOf<Variable, Variable>()

        fun VariableState.instantiate() =
            VariableState(lowerBounds.map { it.instantiateAbove(lvl) }, upperBounds.map { it.instantiateAbove(lvl) })

        return postWalk {
            if (this is Variable && this.lvl > lvl) tvs.computeIfAbsent(this) { freshVar(lvl, st = st.instantiate()) }
            else this
        }
    }

    data class Ctx(
        val lvl: Int,
        val vars: Map<String, SimpleType> = emptyMap(),
        val cache: MutableSet<Pair<SimpleType, SimpleType>> = mutableSetOf()
    ) {
        operator fun plus(kv: Pair<String, SimpleType>) = copy(vars = vars + kv)
    }

    private val builtins = mapOf(
        "inc" to Function(Number, Number),
        "if" to freshVar(1).let { v -> Function(Bool, Function(v, Function(v, v))) },
        "false" to Bool
    )

    private fun ctx() = Ctx(lvl = 0, vars = builtins)

    fun Ctx.freshVar(st: VariableState = VariableState()) = freshVar(lvl, st)

    fun Ctx.typeTerm(term: Term): SimpleType = when (term) {
        is App -> freshVar().let { res ->
            constrain(typeTerm(term.lhs), Function(typeTerm(term.rhs), res))
            res
        }

        is Lam -> freshVar().let { param ->
            Function(param, (this + (term.name to param)).typeTerm(term.rhs))
        }

        is Num -> Number
        is Rcd -> Record(term.fields.mapValues { typeTerm(it.value) })
        is Sel -> freshVar().let { res ->
            constrain(typeTerm(term.receiver), Record(mapOf(term.fieldName to res)))
            res
        }

        is Var -> (vars[term.name] ?: err("not found: ${term.name}")).instantiateAbove(this.lvl)
    }

    fun Ctx.constrain(lhs: SimpleType, rhs: SimpleType) {
        if ((lhs to rhs) in cache) return

        if (lhs is Variable) {
            lhs.st.upperBounds += rhs
            lhs.st.lowerBounds.forEach { constrain(it, rhs) }
            return
        } else when (rhs) {
            is Variable -> {
                rhs.st.lowerBounds += lhs
                rhs.st.upperBounds.forEach { constrain(lhs, it) }
                return
            }

            is Function -> if (lhs is Function) {
                constrain(rhs.lhs, lhs.lhs)
                constrain(lhs.rhs, rhs.rhs)
                return
            }

            Number -> if (lhs is Number) return
            Bool -> if (lhs is Bool) return

            is Record -> if (lhs is Record) {
                rhs.fields.forEach { (field, rightType) ->
                    lhs.fields[field]?.let { leftType ->
                        constrain(leftType, rightType)
                        return
                    }
                }
            }
        }

        err("can't constrain: $lhs <= $rhs")
    }

    fun Term.typeTerm(): SimpleType = ctx().typeTerm(this)

    fun SimpleType.compact(): CompactTypeScheme {
        val recursive: MutableMap<PolarVariable, Variable> = mutableMapOf()
        var recVars: Map<Variable, CompactType> = emptyMap()

        fun Set<PolarVariable>.go(t: SimpleType, p: Polarity): CompactType = when (t) {
            is Function -> CompactType(fn = CompactFunction(go(t.lhs, !p), go(t.rhs, p)))
            Number -> CompactType(prims = setOf(Number))
            Bool -> CompactType(prims = setOf(Bool))
            is Record -> CompactType(rcd = CompactRecord(t.fields.mapValues { go(it.value, p) }))
            is Variable -> {
                val pv = PolarVariable(t.st, p)

                if (pv in this) {
                    CompactType(vars = setOf(recursive.computeIfAbsent(pv) { freshVar(0) }))
                } else {
                    val bounds = when (p) {
                        POS -> t.st.lowerBounds
                        NEG -> t.st.upperBounds
                    }

                    with(this + pv) {
                        (bounds.map { go(it, p) } + CompactType(vars = setOf(t))).merge(p)
                    }
                }
            }
        }

        return CompactTypeScheme(emptySet<PolarVariable>().go(this, POS), recVars)
    }

    fun CompactTypeScheme.coalesce(): Type {
        fun Map<Pair<CompactTypeOrVariable, Polarity>, () -> TypeVariable>.go(
            ctov: CompactTypeOrVariable,
            p: Polarity
        ): Type {
            this[ctov to p]?.let { f -> return f() }

            return when (ctov) {
                is CompactType -> ctov.run {
                    val types = vars.map { go(it, p) }.toMutableSet()

                    types += prims.map { prim ->
                        when (prim) {
                            Number -> NumberType
                            Bool -> Type.BoolType
                        }
                    }

                    rcd?.let { rcd -> types += RecordType(rcd.fields.mapValues { go(it.value, p) }) }
                    fn?.let { fn -> types += FunctionType(go(fn.lhs, !p), go(fn.rhs, p)) }

                    when (types.size) {
                        0 -> p.mzero
                        1 -> types.first()
                        else -> p.mplus(types)
                    }
                }

                is Variable -> recVars[ctov]?.let { go(it, p) } ?: TypeVariable(ctov.uniqueName)
            }
        }

        return emptyMap<Pair<CompactTypeOrVariable, Polarity>, () -> TypeVariable>().go(type, POS)
    }
}

fun main() {
    fun inferType(term: Term) {
        with(Typer()) {
            println(
                term.typeTerm()
                    .compact()
                    .coalesce()
            )
        }
    }

    inferType(App(Var("inc"), Num(42)))
    inferType(Lam("x", App(Var("inc"), Var("x"))))
    inferType(Lam("x", Sel(Rcd(mapOf("field" to App(Var("inc"), Var("x")))), "field")))
    inferType(App(App(App(Var("if"), Var("false")), Rcd(mapOf("a" to Num(42), "b" to Num(12)))), Rcd(mapOf("a" to Num(32)))))
    inferType(Lam("x", Rcd(mapOf("a" to Sel(Var("x"), "a"), "b" to Sel(Var("x"), "b")))))
    inferType(App(App(App(Var("if"), Var("false")), Num(42)), Rcd(mapOf("a" to Num(32)))))
}
