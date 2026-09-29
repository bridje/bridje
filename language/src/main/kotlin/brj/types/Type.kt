package brj.types

import brj.runtime.QSymbol
import brj.runtime.Symbol
import java.util.concurrent.atomic.AtomicInteger

// τ, or τ ∨ nil. The flag adds nil to the base and never forbids it: on a variable, (α, false) is
// whatever α's bounds admit, nil included. Nil is Nothing?.
data class Type(val base: Base, val nullable: Boolean = false) {
    override fun toString() = if (nullable) "$base?" else "$base"
}

fun Type.nullable(): Type = if (nullable) this else copy(nullable = true)

val Type.bareVar: TypeVar? get() = if (nullable) null else base as? TypeVar

sealed interface Base {
    data class Prim(val kind: PrimKind) : Base {
        override fun toString() = kind.display
    }

    data object Nothing : Base {
        override fun toString() = "Nothing"
    }

    data class Fn(val paramTypes: List<Type>, val returnType: Type) : Base {
        override fun toString() = "Fn([${paramTypes.joinToString(", ")}] $returnType)"
    }

    data class Vector(val el: Type) : Base {
        override fun toString() = "[$el]"
    }

    data class Set(val el: Type) : Base {
        override fun toString() = "#{$el}"
    }

    // A JVM class. Type arguments are invariant, as Java's are; an empty argument list is the erased form.
    data class Host(val className: String, val args: List<Type> = emptyList()) : Base {
        override fun toString(): String {
            val simple = className.substringAfterLast('.')
            return if (args.isEmpty()) simple else "$simple(${args.joinToString(", ")})"
        }
    }

    data class Iterable(val el: Type) : Base {
        override fun toString() = "Iterable($el)"
    }

    data class Iterator(val el: Type) : Base {
        override fun toString() = "Iterator($el)"
    }

    // A record: the keys it carries, under a tag's or an enum's name, or none. A record is a tag with no name.
    // - [args] are [name]'s type arguments, which give the keys its declaration names their instances.
    // - [keys] are the keys beyond those the name declares: for a record with no name, all of them.
    // - [closed]: the record carries no key it does not mention, as a literal does. An open one, as a type
    //   form is, may carry others, at any instance.
    // A tag with no record, `Nothing`, or an opaque builtin, `Symbol`, is a Rec with a name and no keys, which
    // the context knows is not a record.
    data class Rec(val name: Name?, val args: List<Type>, val keys: Map<QSymbol, Slot>, val closed: Boolean) : Base {
        init {
            require(name != null || args.isEmpty()) { "a record with no name has no type arguments: $args" }
        }

        override fun toString(): String {
            val withArgs = name?.let { n -> if (args.isEmpty()) "$n" else "$n(${args.joinToString(", ")})" }
            return when {
                withArgs == null -> "{${slotsString(keys)}}"
                keys.isEmpty() -> withArgs
                else -> "$withArgs{${slotsString(keys)}}"
            }
        }
    }

    // `{K & a}`: whatever the variable a is, with the keys K as given: the type of `with` on a variable.
    // It is never a variable's concrete bound.
    data class OnVar(val keys: Map<QSymbol, Slot>, val base: TypeVar) : Base {
        override fun toString(): String = if (keys.isEmpty()) "{& $base}" else "{${slotsString(keys)} & $base}"
    }
}

// What a record's type says of one of its keys: whether it is certainly present, and the instance its value
// is at, the key's declared type at [args]. A key declared without type variables has no arguments.
data class Slot(val required: Boolean, val args: List<Type> = emptyList())

internal fun slotString(key: QSymbol, slot: Slot): String {
    val k = if (slot.required) key.toDisplayString() else key.toDisplayString().replaceFirst(".", ".?")
    return if (slot.args.isEmpty()) k else "$k(${slot.args.joinToString(", ")})"
}

private fun slotsString(keys: Map<QSymbol, Slot>) = keys.entries.joinToString(", ") { (k, s) -> slotString(k, s) }

// A record's name: the tag it is, or the enum whose variants it is one of.
sealed interface Name

enum class PrimKind(val display: String) {
    INT("Int"), DOUBLE("Double"), BIG_INT("BigInt"), BIG_DEC("BigDec"), STR("Str"), BOOL("Bool"), BYTES("Bytes")
}

data class TagRef(val ns: Symbol, val name: Symbol) : Name {
    override fun toString() = name.name
}

data class EnumRef(val ns: Symbol, val name: Symbol) : Name {
    override fun toString() = name.name
}

// A type variable is an identity. What has flowed into it and what has been demanded of it live in a
// BoundEnv, so a typing scheme is a value and two branches can extend the same scheme independently.
// A rigid variable is one a declaration holds abstract while the definition is checked against it (checkDeclared).
class TypeVar(val rigid: Boolean = false) : Base {
    val id = nextId.getAndIncrement()

    // An identity, hashed by its id so that sets and maps of variables iterate in the order they were made.
    override fun hashCode() = id

    override fun toString() = "t$id"

    companion object {
        private val nextId = AtomicInteger()
    }
}

val IntType = Type(Base.Prim(PrimKind.INT))
val DoubleType = Type(Base.Prim(PrimKind.DOUBLE))
val BigIntType = Type(Base.Prim(PrimKind.BIG_INT))
val BigDecType = Type(Base.Prim(PrimKind.BIG_DEC))
val StrType = Type(Base.Prim(PrimKind.STR))
val BoolType = Type(Base.Prim(PrimKind.BOOL))
val BytesType = Type(Base.Prim(PrimKind.BYTES))
val NothingType = Type(Base.Nothing)

fun freshVar(): Type = Type(TypeVar())
fun TypeVar.type(nullable: Boolean = false): Type = Type(this, nullable)
fun FnType(paramTypes: List<Type>, returnType: Type): Type = Type(Base.Fn(paramTypes, returnType))
fun VectorType(el: Type): Type = Type(Base.Vector(el))
fun SetType(el: Type): Type = Type(Base.Set(el))
fun HostType(className: String, args: List<Type> = emptyList()): Type = Type(Base.Host(className, args))
fun IterableType(el: Type): Type = Type(Base.Iterable(el))
fun IteratorType(el: Type): Type = Type(Base.Iterator(el))
fun RecType(name: Name?, args: List<Type>, keys: Map<QSymbol, Slot>, closed: Boolean = false): Type =
    Type(Base.Rec(name, args, keys, closed))
fun OnVarType(keys: Map<QSymbol, Slot>, base: TypeVar): Type = Type(Base.OnVar(keys, base))

private fun present(keys: Set<QSymbol>) = keys.associateWith { Slot(true) }

// An open record certainly carrying [keys], each declared without type variables; on a variable where [base] is given.
fun RecordType(keys: Set<QSymbol>, base: TypeVar? = null): Type =
    base?.let { OnVarType(present(keys), it) } ?: RecType(null, emptyList(), present(keys))
fun TagType(tag: TagRef, args: List<Type>, keys: Set<QSymbol> = emptySet()): Type = RecType(tag, args, present(keys))
fun EnumType(enum: EnumRef, args: List<Type>, keys: Set<QSymbol> = emptySet()): Type = RecType(enum, args, present(keys))

val FormEnum = EnumRef(Symbol.intern("brj.rdr"), Symbol.intern("Form"))
val FormType = EnumType(FormEnum, emptyList())

// What `throw` raises and a `catch` catches: one of the builtin anomaly tags, a host exception among them as `Host`.
val AnomalyEnum = EnumRef(Symbol.intern("brj.core"), Symbol.intern("Anomaly"))
val AnomalyType = EnumType(AnomalyEnum, emptyList())

internal fun Base.mapTypes(f: (Type) -> Type): Base = when (this) {
    is Base.Fn -> Base.Fn(paramTypes.map(f), f(returnType))
    is Base.Vector -> Base.Vector(f(el))
    is Base.Set -> Base.Set(f(el))
    is Base.Iterable -> Base.Iterable(f(el))
    is Base.Iterator -> Base.Iterator(f(el))
    is Base.Host -> Base.Host(className, args.map(f))
    is Base.Rec -> copy(args = args.map(f), keys = keys.mapSlotArgs(f))
    is Base.OnVar -> copy(keys = keys.mapSlotArgs(f))
    is Base.Prim, Base.Nothing, is TypeVar -> this
}

internal fun Map<QSymbol, Slot>.mapSlotArgs(f: (Type) -> Type): Map<QSymbol, Slot> =
    mapValues { (_, s) -> if (s.args.isEmpty()) s else s.copy(args = s.args.map(f)) }

internal fun Type.typeVars(): LinkedHashSet<TypeVar> {
    val out = LinkedHashSet<TypeVar>()
    fun go(t: Type) {
        when (val b = t.base) {
            is TypeVar -> out += b
            is Base.OnVar -> { b.mapTypes { go(it); it }; out += b.base }
            else -> b.mapTypes { go(it); it }
        }
    }
    go(this)
    return out
}

// The lower bound of a variable is a set of disjuncts, kept in normal form: one concrete product (same-kind
// concretes merge by the kind's join, and nil sets its flag), bare variables, and at most one `{K & α}`
// per variable α. `{K1 & α} ∨ {K2 & α}` keeps the keys both give, and `{K & α} ∨ α` is `α` where K's keys
// have one type each, so giving them changes nothing α could not already hold. A lower `α?` puts its nil in
// the concrete product and α among the variables, so variable edges here carry no flag.
data class LowerBound(
    // Nothing, when nothing concrete has flowed in.
    val concrete: Type = NothingType,
    val tvs: Set<TypeVar> = emptySet(),
    val meets: Map<TypeVar, Map<QSymbol, Slot>> = emptyMap(),
) {
    init {
        require(concrete.base !is TypeVar && concrete.base !is Base.OnVar) { "a concrete lower bound is not a variable: $concrete" }
    }

    val isEmpty get() = concrete == NothingType && tvs.isEmpty() && meets.isEmpty()

    val nullable get() = concrete.nullable

    fun concreteType(): Type? = concrete.takeIf { it != NothingType }

    fun disjuncts(): List<Type> = listOfNotNull(concreteType()) + meets.map { (tv, keys) -> OnVarType(keys, tv) }

    fun asTypes(): List<Type> = disjuncts() + tvs.map { it.type() }
}

// The upper bound holds at most one concrete product (same-kind concretes merge by the kind's meet, and
// across kinds the meet is Nothing), and the variables it flows into, each with whether nil may go too:
// α ≤ β? is the edge (β, true), along which what flows into α flows into β with nil stripped.
data class UpperBound(
    // May be Nothing.
    val concrete: Type? = null,
    val tvs: Map<TypeVar, Boolean> = emptyMap(),
) {
    init {
        require(concrete?.base !is TypeVar && concrete?.base !is Base.OnVar) { "a concrete upper bound is not a variable: $concrete" }
    }

    val isEmpty get() = concrete == null && tvs.isEmpty()

    fun asTypes(): List<Type> = listOfNotNull(concrete) + tvs.map { (tv, nilOk) -> tv.type(nilOk) }
}

data class Bounds(val lower: LowerBound = LowerBound(), val upper: UpperBound = UpperBound())

// A variable absent from the map is unconstrained.
typealias BoundEnv = Map<TypeVar, Bounds>

val BoundEnv.lower: (TypeVar) -> LowerBound get() = { this[it]?.lower ?: LowerBound() }
val BoundEnv.upper: (TypeVar) -> UpperBound get() = { this[it]?.upper ?: UpperBound() }

// [provenance] is the constraint being checked when the failure arose, where the failing pair is a
// fragment of it: `Int is not a Str` alone does not say which call put an Int where a Str was wanted.
open class TypeCheckException(message: String, val provenance: Pair<Type, Type>? = null) : RuntimeException(
    provenance?.let { (l, u) -> "$message\n  while checking $l ≤ $u" } ?: message
)
