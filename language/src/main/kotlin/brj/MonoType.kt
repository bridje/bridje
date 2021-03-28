package brj

import java.lang.String.format

sealed class MonoType

class TypeVar(val s: String = "_") : MonoType() {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun toString(): String {
        return "$s#${format("%x", hashCode().toShort())}"
    }
}

object IntType : MonoType() {
    override fun toString() = "Int"
}

object BoolType : MonoType() {
    override fun toString() = "Bool"
}

object StringType : MonoType() {
    override fun toString() = "Str"
}

data class VectorType(val elType: MonoType) : MonoType() {
    override fun toString() = "[$elType]"
}

data class SetType(val elType: MonoType) : MonoType() {
    override fun toString() = "#{$elType}"
}

data class FnType(val paramTypes: List<MonoType>, val resType: MonoType) : MonoType() {
    override fun toString(): String {
        return "(Fn (${paramTypes.joinToString(" ")}) $resType)"
    }
}
