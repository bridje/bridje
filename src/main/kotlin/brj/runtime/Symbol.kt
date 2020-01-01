package brj.runtime

import brj.emitter.BridjeObject

sealed class Ident : BridjeObject {
    abstract val kind: SymKind
}

enum class SymKind(internal val prefix: String) {
    ID(""), TYPE(""), RECORD(":"), VARIANT(":");

    internal fun toString(baseStr: String) = "$prefix$baseStr"
    internal fun toString(ns: String, baseStr: String) = "$prefix$ns/$baseStr"
}

class Symbol private constructor(override val kind: SymKind, val baseStr: String): Ident() {
    override fun equals(other: Any?) =
        this === other || (other is Symbol && kind == other.kind && baseStr == other.baseStr)

    override fun hashCode() = 31 * kind.hashCode() + baseStr.hashCode()

    override fun toString() = kind.toString(baseStr)

    companion object {
        operator fun invoke(kind: SymKind, local: String) = Symbol(kind, local.intern())
    }
}

class QSymbol internal constructor(val ns: Symbol, val local: Symbol): Ident() {
    init {
        assert (ns.kind.prefix.isEmpty())
    }

    override val kind = local.kind

    private val str = kind.toString(ns.baseStr, local.baseStr)

    override fun toString() = str

    override fun equals(other: Any?) =
        this === other || (other is QSymbol && kind == other.kind && ns == other.ns && local == other.local)

    override fun hashCode(): Int = (kind.hashCode() * 31 + ns.hashCode()) * 31 + local.hashCode()

    companion object {
        operator fun invoke(ns: Symbol, local: Symbol) = QSymbol(ns, local)
        operator fun invoke(ns: Symbol, kind: SymKind, local: String) = QSymbol(ns, Symbol(kind, local.intern()))
    }
}
