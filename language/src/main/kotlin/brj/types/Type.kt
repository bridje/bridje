package brj.types

import brj.runtime.QSymbol
import brj.runtime.Symbol
import java.util.concurrent.atomic.AtomicInteger

sealed interface Type

enum class PrimKind(val display: String) {
    INT("Int"), DOUBLE("Double"), BIG_INT("BigInt"), BIG_DEC("BigDec"), STR("Str"), BOOL("Bool"), BYTES("Bytes")
}

data class PrimType(val kind: PrimKind) : Type {
    override fun toString() = kind.display
}

val IntType = PrimType(PrimKind.INT)
val DoubleType = PrimType(PrimKind.DOUBLE)
val BigIntType = PrimType(PrimKind.BIG_INT)
val BigDecType = PrimType(PrimKind.BIG_DEC)
val StrType = PrimType(PrimKind.STR)
val BoolType = PrimType(PrimKind.BOOL)
val BytesType = PrimType(PrimKind.BYTES)

data object BottomType : Type {
    override fun toString() = "Nothing"
}

data object NilType : Type {
    override fun toString() = "Nothing?"
}

// T? for a T that is not itself nullable, nil or Nothing.
data class NullableType(val inner: Type) : Type {
    override fun toString() = "$inner?"
}

data class FnType(val paramTypes: List<Type>, val returnType: Type) : Type {
    override fun toString() = "Fn([${paramTypes.joinToString(", ")}] $returnType)"
}

data class VectorType(val el: Type) : Type {
    override fun toString() = "[$el]"
}

data class SetType(val el: Type) : Type {
    override fun toString() = "#{$el}"
}

// A JVM class. Type arguments are invariant, as Java's are; an empty argument list is the erased form.
data class HostType(val className: String, val args: List<Type> = emptyList()) : Type {
    override fun toString(): String {
        val simple = className.substringAfterLast('.')
        return if (args.isEmpty()) simple else "$simple(${args.joinToString(", ")})"
    }
}

data class IterableType(val el: Type) : Type {
    override fun toString() = "Iterable($el)"
}

data class IteratorType(val el: Type) : Type {
    override fun toString() = "Iterator($el)"
}

// A record type is the set of keys known to be present. Key value types are global, so it carries none.
// {} is the top of the record kind: any record satisfies it.
data class RecordType(val keys: Set<QSymbol>) : Type {
    override fun toString() = keys.joinToString(", ", "{", "}") { it.toDisplayString() }
}

data class TagRef(val ns: Symbol, val name: Symbol) {
    override fun toString() = name.name
}

data class EnumRef(val ns: Symbol, val name: Symbol) {
    override fun toString() = name.name
}

val FormEnum = EnumRef(Symbol.intern("brj.rdr"), Symbol.intern("Form"))
val FormType = EnumType(FormEnum, emptyList())

// A tag's type arguments are its enum's, in the enum's order. A standalone tag's are its own.
data class TagType(val tag: TagRef, val args: List<Type>) : Type {
    override fun toString() = if (args.isEmpty()) "$tag" else "$tag(${args.joinToString(", ")})"
}

data class EnumType(val enum: EnumRef, val args: List<Type>) : Type {
    override fun toString() = if (args.isEmpty()) "$enum" else "$enum(${args.joinToString(", ")})"
}

// What `with` and nil-narrowing know about a value beyond its base type. `record` implies `notNil`.
data class Filter(val record: Boolean = false, val keys: Set<QSymbol> = emptySet(), val notNil: Boolean = false) {
    val isIdentity get() = !record && !notNil

    // Satisfied by a value passing either filter.
    infix fun or(o: Filter) = Filter(
        record = record && o.record,
        keys = if (record && o.record) keys intersect o.keys else emptySet(),
        notNil = notNil && o.notNil,
    )

    infix fun and(o: Filter) = Filter(
        record = record || o.record,
        keys = keys + o.keys,
        notNil = notNil || o.notNil || record || o.record,
    )

    override fun toString() = when {
        record -> RecordType(keys).toString()
        notNil -> "¬nil"
        else -> "⊤"
    }

    companion object {
        val NOT_NIL = Filter(notNil = true)
        fun keys(keys: Set<QSymbol>) = Filter(record = true, keys = keys, notNil = true)
    }
}

// base ∧ filter: whatever the base is, narrowed by the filter. The type of `with` on a variable or a
// nominal, and of a nil-checked variable. The base is a variable, a tag or an enum; on anything else
// the meet normalises away. It appears in positive position and as a lower bound; as an upper bound it
// decomposes.
data class Meet(val base: Type, val filter: Filter) : Type {
    override fun toString() = "$base ∧ $filter"
}

fun Type.nullable(): Type = when (this) {
    NilType, is NullableType -> this
    BottomType -> NilType
    else -> NullableType(this)
}

// A type variable is an identity. What has flowed into it and what has been demanded of it live in a
// BoundEnv, so a typing scheme is a value and two branches can extend the same scheme independently.
class TypeVar : Type {
    val id = nextId.getAndIncrement()

    override fun toString() = "t$id"

    companion object {
        private val nextId = AtomicInteger()
    }
}

internal fun Type.typeVars(): Set<TypeVar> {
    val out = LinkedHashSet<TypeVar>()
    fun go(t: Type) {
        when (t) {
            is TypeVar -> out += t
            is NullableType -> go(t.inner)
            is FnType -> { t.paramTypes.forEach(::go); go(t.returnType) }
            is VectorType -> go(t.el)
            is SetType -> go(t.el)
            is IterableType -> go(t.el)
            is IteratorType -> go(t.el)
            is HostType -> t.args.forEach(::go)
            is TagType -> t.args.forEach(::go)
            is EnumType -> t.args.forEach(::go)
            is Meet -> go(t.base)
            is PrimType, is RecordType, BottomType, NilType -> {}
        }
    }
    go(this)
    return out
}

// The lower bound of a variable is a set of disjuncts, kept in normal form: at most one concrete type
// (same-kind concretes merge by the kind's join), a nullability flag, bare variables, and at most one
// `α ∧ F` per variable α. `(α ∧ F1) ∨ (α ∧ F2)` is `α ∧ (F1 or F2)`, and `(α ∧ F) ∨ α` is `α`.
data class LowerBound(
    // Never nil, nullable, Nothing, a type variable or a meet on a variable.
    val concrete: Type? = null,
    val nullable: Boolean = false,
    val tvs: Set<TypeVar> = emptySet(),
    val meets: Map<TypeVar, Filter> = emptyMap(),
) {
    val isEmpty get() = concrete == null && !nullable && tvs.isEmpty() && meets.isEmpty()

    fun concreteType(): Type? = when {
        concrete != null -> if (nullable) NullableType(concrete) else concrete
        nullable -> NilType
        else -> null
    }

    fun disjuncts(): List<Type> = listOfNotNull(concreteType()) + meets.map { (tv, f) -> Meet(tv, f) }

    fun asTypes(): List<Type> = disjuncts() + tvs
}

// The upper bound holds at most one concrete type (same-kind concretes merge by the kind's meet, and
// across kinds the meet is Nothing), whether nil is admitted, and bare variables.
data class UpperBound(
    // Never nil, nullable or a type variable. May be Nothing.
    val concrete: Type? = null,
    val nilOk: Boolean = true,
    val tvs: Set<TypeVar> = emptySet(),
) {
    val isEmpty get() = concrete == null && tvs.isEmpty()

    fun concreteType(): Type? = when {
        concrete == null -> null
        concrete == BottomType -> if (nilOk) NilType else BottomType
        nilOk -> NullableType(concrete)
        else -> concrete
    }

    fun asTypes(): List<Type> = listOfNotNull(concreteType()) + tvs
}

data class Bounds(val lower: LowerBound = LowerBound(), val upper: UpperBound = UpperBound())

// A variable absent from the map is unconstrained.
typealias BoundEnv = Map<TypeVar, Bounds>

val BoundEnv.lower: (TypeVar) -> LowerBound get() = { this[it]?.lower ?: LowerBound() }
val BoundEnv.upper: (TypeVar) -> UpperBound get() = { this[it]?.upper ?: UpperBound() }

// [provenance] is the constraint being checked when the failure arose, where the failing pair is a
// fragment of it: `Int is not a Str` alone does not say which call put an Int where a Str was wanted.
class TypeCheckException(message: String, val provenance: Pair<Type, Type>? = null) : RuntimeException(
    provenance?.let { (l, u) -> "$message\n  while checking $l ≤ $u" } ?: message
)
