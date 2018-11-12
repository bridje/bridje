package brj

private class Interner<K, V>(val f: (K) -> V) {
    val interned = mutableMapOf<K, V>()

    fun intern(k: K): V {
        return interned.getOrPut(k) { f(k) }
    }
}

sealed class Ident {
    abstract val name: Symbol
}

class Symbol private constructor(val nameStr: String) : Ident()  {
    override val name: Symbol get() = this

    companion object {
        private val INTERNER = Interner(::Symbol)
        fun intern(name: String) = INTERNER.intern(name)
    }

    override fun toString() = nameStr
}

class QSymbol private constructor(val ns: Symbol, override val name: Symbol): Ident() {
    companion object {
        private val INTERNER: Interner<Pair<Symbol, Symbol>, QSymbol> = Interner { QSymbol(it.first, it.second) }
        fun intern(ns: Symbol, name: Symbol) = INTERNER.intern(Pair(ns, name))
    }

    override fun toString() = "$ns/$name"
}

