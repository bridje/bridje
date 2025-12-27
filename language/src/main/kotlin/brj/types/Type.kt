package brj.types

import brj.*
import brj.types.Nullability.*

class TypeVar {
    override fun toString(): String = "T${hashCode().toString(16).take(4)}"
}

enum class Nullability {
    NOT_NULL, MAYBE_NULL, NULLABLE
}

data class Type (
    val nullability: Nullability,
    val tv: TypeVar,
    val base: BaseType?
)

sealed interface BaseType

data object IntType: BaseType
data object FloatType: BaseType
data object BoolType: BaseType
data object StringType: BaseType
data object BigIntType: BaseType
data object BigDecType: BaseType

data class VectorType(val el: Type): BaseType

fun BaseType.nullable(tv: TypeVar = TypeVar()) = Type(NULLABLE, tv, this)
fun BaseType.notNull(tv: TypeVar = TypeVar()) = Type(NOT_NULL, tv, this)
fun freshType(tv: TypeVar = TypeVar()) = Type(MAYBE_NULL, tv, null)
fun nullType(tv: TypeVar = TypeVar()) = Type(NULLABLE, tv, null)

private val Type.tvs0: List<TypeVar> get() = 
    when (val base = this.base) {
        is VectorType -> base.el.tvs0
        else -> emptyList()
    }.plus(tv)

val Type.tvs: List<TypeVar> get() = tvs0.distinct()

fun ValueExpr.checkType(): Type = typing().type
