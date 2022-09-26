package brj

import brj.runtime.Symbol
import java.lang.String.format

sealed class MonoType {
    open fun walk(outer: MonoType.() -> MonoType, inner: MonoType.() -> MonoType) = this.outer()
    fun postWalk(f: MonoType.() -> MonoType): MonoType = walk(f) { postWalk(f) }
}

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

    override fun walk(outer: (MonoType) -> MonoType, inner: (MonoType) -> MonoType) =
        outer(VectorType(inner(elType)))
}

data class SetType(val elType: MonoType) : MonoType() {
    override fun toString() = "#{$elType}"

    override fun walk(outer: (MonoType) -> MonoType, inner: (MonoType) -> MonoType) =
        outer(SetType(inner(elType)))
}

data class RecordType(val entryTypes: Map<Symbol, MonoType>) : MonoType() {
    operator fun get(sym: Symbol) = entryTypes[sym]

    override fun toString() =
        entryTypes.asIterable()
            .joinToString(prefix = "{", transform = { ":${it.key} ${it.value}" }, separator = ", ", postfix = "}")

    override fun walk(outer: (MonoType) -> MonoType, inner: (MonoType) -> MonoType) =
        outer(RecordType(entryTypes.mapValues { inner(it.value) }))
}

data class FnType(val paramTypes: List<MonoType>, val resType: MonoType) : MonoType() {
    override fun toString() = "(Fn (${paramTypes.joinToString(" ")}) $resType)"

    override fun walk(outer: (MonoType) -> MonoType, inner: (MonoType) -> MonoType) =
        outer(FnType(paramTypes.map(inner), inner(resType)))
}
