package brj

private class Interner<K, V>(val f: (K) -> V) {
    val interned = mutableMapOf<K, V>()

    fun intern(k: K): V {
        return interned.getOrPut(k) { f(k) }
    }
}

sealed class Ident

sealed class LocalIdent : Ident() {
    abstract val name: Symbol
}

sealed class GlobalIdent(val ns: Symbol, val name: Symbol) : Ident()

class Symbol private constructor(val nameStr: String) : LocalIdent() {
    override val name get() = this

    companion object {
        private val INTERNER = Interner(::Symbol)
        fun intern(name: String) = INTERNER.intern(name)
    }

    override fun toString() = nameStr
}

class Keyword private constructor(nameStr: String) : LocalIdent() {
    override val name = Symbol.intern(nameStr)

    companion object {
        private val INTERNER = Interner(::Keyword)
        fun intern(name: String) = INTERNER.intern(name)
    }

    override fun toString() = ":$name"
}


class QSymbol private constructor(ns: Symbol, name: Symbol) : GlobalIdent(ns, name) {
    companion object {
        private val INTERNER: Interner<Pair<Symbol, Symbol>, QSymbol> = Interner { QSymbol(it.first, it.second) }
        fun intern(ns: Symbol, name: Symbol) = INTERNER.intern(Pair(ns, name))
    }

    override fun toString() = "$ns/$name"
}

class QKeyword private constructor(ns: Symbol, name: Symbol) : GlobalIdent(ns, name) {
    companion object {
        private val INTERNER: Interner<Pair<Symbol, Symbol>, QKeyword> = Interner { QKeyword(it.first, it.second) }
        fun intern(ns: Symbol, name: Symbol) = INTERNER.intern(Pair(ns, name))
    }

    override fun toString() = ":$ns/$name"
}

