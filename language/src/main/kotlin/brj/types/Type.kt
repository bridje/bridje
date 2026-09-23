package brj.types

import brj.runtime.Symbol
import brj.types.Nullability.*
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

class TypeVar {
    override fun toString(): String = "T${hashCode().toString(16).take(4)}"
}

enum class Nullability {
    NOT_NULL, MAYBE_NULL, NULLABLE
}

@ExportLibrary(InteropLibrary::class)
data class Type (
    val nullability: Nullability,
    val tv: TypeVar,
    val base: BaseType?
) : TruffleObject {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()

    override fun toString(): String {
        val baseStr = base?.toString() ?: "?"
        return when (nullability) {
            NULLABLE -> "$baseStr?"
            else -> baseStr
        }
    }
}

sealed interface BaseType : TruffleObject

@ExportLibrary(InteropLibrary::class)
data object IntType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Int"
}

@ExportLibrary(InteropLibrary::class)
data object FloatType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Double"
}

@ExportLibrary(InteropLibrary::class)
data object BoolType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Bool"
}

@ExportLibrary(InteropLibrary::class)
data object StringType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Str"
}

@ExportLibrary(InteropLibrary::class)
data object BigIntType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "BigInt"
}

@ExportLibrary(InteropLibrary::class)
data object BigDecType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "BigDec"
}

@ExportLibrary(InteropLibrary::class)
data object RecordType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Record"
}

@ExportLibrary(InteropLibrary::class)
data class TagType(val ns: Symbol, val name: Symbol, val args: List<Type> = emptyList(), val variances: List<Variance> = emptyList()): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
    override fun toString(): String {
        val base = "$ns.$name"
        return if (args.isEmpty()) base else "$base(${args.joinToString(", ")})"
    }
}

@ExportLibrary(InteropLibrary::class)
data class EnumType(val name: Symbol, val args: List<Type> = emptyList(), val variances: List<Variance> = emptyList()): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
    override fun toString(): String =
        if (args.isEmpty()) "$name" else "$name(${args.joinToString(", ")})"
}

enum class Variance { IN, OUT, INVARIANT }

@ExportLibrary(InteropLibrary::class)
data object FormType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Form"
}

@ExportLibrary(InteropLibrary::class)
data class HostType(val className: String, val args: List<Type> = emptyList(), val variances: List<Variance> = emptyList()): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString(): String = when {
        args.isEmpty() -> className.substringAfterLast('.')
        else -> "${className.substringAfterLast('.')}(${args.joinToString(", ")})"
    }
}

@ExportLibrary(InteropLibrary::class)
data object ErrorType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "<error>"
}

@ExportLibrary(InteropLibrary::class)
data class VectorType(val el: Type): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "[${el}]"
}
@ExportLibrary(InteropLibrary::class)
data class SetType(val el: Type): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "#{${el}}"
}

// Virtual protocol types — no Java class backs these.
// They represent Truffle iterator protocol capabilities.
// See #78: Truffle intercepts java.lang.Iterable on TruffleObjects,
// so BridjeVector can't implement it. These exist in the type system only.
@ExportLibrary(InteropLibrary::class)
data class IterableType(val el: Type): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Iterable(${el})"
}

@ExportLibrary(InteropLibrary::class)
data class IteratorType(val el: Type): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Iterator(${el})"
}

// Virtual type — no dedicated runtime class.
// At runtime a Bytes value is a Truffle HostObject wrapping a Java `byte[]`
// (produced via `env.asGuestValue(byte[])`).
// Builtins that consume Bytes unwrap via `env.asHostObject` back to `byte[]`;
// this fast path is valid because v1 only produces Bytes through our own
// builtins (`by/fromStr`, `fs/fromBytes`).
// Reads through Bytes/nth return the unsigned byte value widened to Int
// (0..255); there is no Byte scalar type in Bridje.
@ExportLibrary(InteropLibrary::class)
data object BytesType: BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString() = "Bytes"
}

@ExportLibrary(InteropLibrary::class)
data class FnType(val paramTypes: List<Type>, val returnType: Type): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = toString()
    override fun toString(): String {
        val params = paramTypes.joinToString(", ")
        return "Fn([$params] $returnType)"
    }
}

fun BaseType.nullable(tv: TypeVar = TypeVar()) = Type(NULLABLE, tv, this)
fun BaseType.notNull(tv: TypeVar = TypeVar()) = Type(NOT_NULL, tv, this)
fun freshType(tv: TypeVar = TypeVar()) = Type(MAYBE_NULL, tv, null)
fun nullType(tv: TypeVar = TypeVar()) = Type(NULLABLE, tv, null)
fun nothingType(tv: TypeVar = TypeVar()) = Type(NOT_NULL, tv, null)
fun errorType() = ErrorType.notNull()
