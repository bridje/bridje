package brj.types2

class TypeVar {
    override fun toString(): String = "T${hashCode().toString(16).take(4)}"
}

class NullabilityVar {
    override fun toString(): String = "N${hashCode().toString(16).take(4)}"
}

data class Type(val base: BaseType, val nullability: Nullability)

sealed interface BaseType

data object IntType : BaseType
data object DoubleType : BaseType
data object BigIntType : BaseType
data object BigDecType : BaseType
data object StringType : BaseType
data object BoolType : BaseType

data class FnType(val params: List<Type>, val ret: Type) : BaseType
data class TypeVarType(val tv: TypeVar) : BaseType

data class Nullability(val value: Value, val nv: NullabilityVar?) {
    enum class Value { NOT_NULL, NULLABLE }
}

typealias BaseSubst = Map<TypeVar, BaseType>
typealias NullabilitySubst = Map<NullabilityVar, Nullability>
