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
    data class Inc(val term: Term) : Term()
    data class Var(val name: String) : Term()
    data class Lam(val name: String, val rhs: Term) : Term()
    data class App(val lhs: Term, val rhs: Term) : Term()
    data class Rcd(val fields: Map<String, Term>) : Term()
    data class Sel(val receiver: Term, val fieldName: String) : Term()
}

class VariableState(
    var lowerBounds: List<SimpleType> = emptyList(),
    var upperBounds: List<SimpleType> = emptyList()
) {
    override fun toString(): String {
        return "(VarSt ${hashCode().toString(16).take(4)}: lower = $lowerBounds; upper = $upperBounds)"
    }
}

sealed interface CompactTypeOrVariable

sealed class SimpleType {
    sealed interface Primitive

    data class Variable(val st: VariableState) : SimpleType(), CompactTypeOrVariable {
        val uniqueName = "tv${hashCode().toString().take(4)}"
    }

    object Number : SimpleType(), Primitive {
        override fun toString() = "Num"
    }

    data class Function(val lhs: SimpleType, val rhs: SimpleType) : SimpleType()
    data class Record(val fields: Map<String, SimpleType>) : SimpleType()
}

fun freshVar() = Variable(VariableState())
fun err(msg: String): Nothing = throw RuntimeException("type error: '$msg'")

data class Ctx(
    val vars: Map<String, SimpleType> = emptyMap(),
    val cache: MutableSet<Pair<SimpleType, SimpleType>> = mutableSetOf()
) {
    operator fun plus(kv: Pair<String, SimpleType>) = copy(vars = vars + kv)
}

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

    is Var -> vars[term.name] ?: err("not found: ${term.name}")

    is Inc -> typeTerm(term.term).also { constrain(it, Number) }
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

        Number -> return

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

fun typeTerm(term: Term): SimpleType = Ctx().typeTerm(term)

enum class Polarity {
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

data class CompactFunction(val lhs: CompactType, val rhs: CompactType)
data class CompactRecord(val fields: Map<String, CompactType>)

data class CompactType(
    val vars: Set<Variable> = emptySet(),
    val prims: Set<Primitive> = emptySet(),
    val rcd: CompactRecord? = null,
    val fn: CompactFunction? = null
) : CompactTypeOrVariable

data class CompactTypeScheme(val type: CompactType, val recVars: Map<Variable, CompactType>)

typealias PolarVariable = Pair<VariableState, Polarity>

fun Iterable<CompactType>.merge(p: Polarity): CompactType =
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
            .flatMap { it.fields.asSequence() }
            .takeIf { it.isNotEmpty() }
            ?.groupBy({ it.key }, { it.value })
            ?.mapValues { it.value.merge(p) }
            ?.let { CompactRecord(it) }

    )

fun SimpleType.compact(): CompactTypeScheme {
    val recursive: MutableMap<PolarVariable, Variable> = mutableMapOf()
    var recVars: Map<Variable, CompactType> = emptyMap()

    fun Set<PolarVariable>.go(t: SimpleType, p: Polarity): CompactType = when (t) {
        is Function -> CompactType(fn = CompactFunction(go(t.lhs, !p), go(t.rhs, p)))
        Number -> CompactType(prims = setOf(Number))
        is Record -> CompactType(rcd = CompactRecord(t.fields.mapValues { go(it.value, p) }))
        is Variable -> {
            val pv = Pair(t.st, p)

            if (pv in this) {
                CompactType(vars = setOf(recursive.computeIfAbsent(pv) { freshVar() }))
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
}

fun CompactTypeScheme.coalesce(): Type {
    fun go(ctov: CompactTypeOrVariable, p: Polarity): Type =
        when (ctov) {
            is CompactType -> ctov.run {
                val types = vars.map { go(it, p) }.toMutableSet()
                types += prims.map { prim ->
                    when (prim) {
                        Number -> NumberType
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

            is Variable -> TypeVariable(ctov.uniqueName)
        }

    return go(type, POS)
}

fun main() {
    println(typeTerm(Inc(Num(42))).compact().coalesce())
    println(typeTerm(Lam("x", Inc(Var("x")))).compact().coalesce())
    println(typeTerm(Lam("x", Sel(Rcd(mapOf("field" to Inc(Var("x")))), "field"))).compact().coalesce())
}