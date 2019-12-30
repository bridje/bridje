package brj.runtime

sealed class Ident {
    abstract val kind: SymKind
}

enum class SymKind(internal val prefix: String) {
    ID(""), TYPE(""), RECORD(":"), VARIANT(":");

    internal fun toString(local: String) = "$prefix$local"
    internal fun toString(ns: String, local: Symbol) = "$prefix$ns/$local"
}

class Symbol private constructor(override val kind: SymKind, val local: String): Ident() {
    override fun equals(other: Any?) =
        this === other || (other is Symbol && kind == other.kind && local == other.local)

    override fun hashCode() = 31 * kind.hashCode() + local.hashCode()

    override fun toString() = kind.toString(local)

    companion object {
        operator fun invoke(kind: SymKind, local: String) = Symbol(kind, local.intern())
    }
}

class QSymbol internal constructor(val ns: Symbol, val local: Symbol): Ident() {
    init {
        assert (ns.kind.prefix.isEmpty())
    }

    override val kind = local.kind

    private val str = kind.toString(ns.local, local)

    override fun toString() = str

    override fun equals(other: Any?) =
        this === other || (other is QSymbol && kind == other.kind && ns == other.ns && local == other.local)

    override fun hashCode(): Int = (kind.hashCode() * 31 + ns.hashCode()) * 31 + local.hashCode()

    companion object {
        operator fun invoke(ns: Symbol, local: Symbol) = QSymbol(ns, local)
        operator fun invoke(ns: Symbol, kind: SymKind, local: String) = QSymbol(ns, Symbol(kind, local.intern()))
    }
}
