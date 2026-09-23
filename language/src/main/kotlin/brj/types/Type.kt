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

fun Type.nullable(): Type = when (this) {
    NilType, is NullableType -> this
    BottomType -> NilType
    else -> NullableType(this)
}

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
            is PrimType, is RecordType, BottomType, NilType -> {}
        }
    }
    go(this)
    return out
}
