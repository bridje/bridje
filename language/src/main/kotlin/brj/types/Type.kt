package brj.types

import brj.analyser.*
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
data class TagType(val ns: String, val name: String, val args: List<Type> = emptyList(), val variances: List<Variance> = emptyList()): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
    override fun toString(): String {
        val base = if (ns.isEmpty()) name else "$ns.$name"
        return if (args.isEmpty()) base else "$base(${args.joinToString(", ")})"
    }
}

@ExportLibrary(InteropLibrary::class)
data class EnumType(val name: String, val args: List<Type> = emptyList(), val variances: List<Variance> = emptyList()): BaseType {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
    override fun toString(): String =
        if (args.isEmpty()) name else "$name(${args.joinToString(", ")})"
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

private val Type.tvs0: List<TypeVar> get() =
    when (val base = this.base) {
        is VectorType -> base.el.tvs0
        is SetType -> base.el.tvs0
        is HostType -> base.args.flatMap { it.tvs0 }
        is TagType -> base.args.flatMap { it.tvs0 }
        is EnumType -> base.args.flatMap { it.tvs0 }
        is FnType -> base.paramTypes.flatMap { it.tvs0 } + base.returnType.tvs0
        else -> emptyList()
    }.plus(tv)

val Type.tvs: List<TypeVar> get() = tvs0.distinct()

private fun instantiateType(type: Type, mapping: MutableMap<TypeVar, TypeVar>): Type {
    fun TypeVar.fresh(): TypeVar = mapping.getOrPut(this) { TypeVar() }

    fun instBase(base: BaseType): BaseType = when (base) {
        is VectorType -> VectorType(instantiateType(base.el, mapping))
        is SetType -> SetType(instantiateType(base.el, mapping))
        is HostType -> if (base.args.isEmpty()) base else HostType(base.className, base.args.map { instantiateType(it, mapping) }, base.variances)
        is TagType -> if (base.args.isEmpty()) base else TagType(base.ns, base.name, base.args.map { instantiateType(it, mapping) }, base.variances)
        is EnumType -> if (base.args.isEmpty()) base else EnumType(base.name, base.args.map { instantiateType(it, mapping) }, base.variances)
        is FnType -> FnType(base.paramTypes.map { instantiateType(it, mapping) }, instantiateType(base.returnType, mapping))
        else -> base
    }

    return Type(type.nullability, type.tv.fresh(), type.base?.let { instBase(it) })
}

fun Type.instantiate(): Type = instantiateType(this, mutableMapOf())

fun ValueExpr.checkType(): Type = typing().type
