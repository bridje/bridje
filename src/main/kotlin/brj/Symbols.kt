package brj

private class Interner<K, V>(val f: (K) -> V) {
    val interned = mutableMapOf<K, V>()

    fun intern(k: K): V {
        return interned.getOrPut(k) { f(k) }
    }
}

interface Ident {
    val base: Symbol
}

interface QIdent : Ident {
    val ns: Symbol
}

class Symbol private constructor(val baseStr: String) : Ident {
    override val base: Symbol get() = this

    companion object {
        private val INTERNER: Interner<String, Symbol> = Interner(::Symbol)

        fun intern(str: String) = INTERNER.intern(str)
    }

    override fun toString() = baseStr
}

class QSymbol private constructor(override val ns: Symbol, override val base: Symbol) : QIdent {
    companion object {
        private val INTERNER: Interner<Pair<Symbol, Symbol>, QSymbol> = Interner { (ns, base) ->
            QSymbol(ns, base)
        }

        fun intern(ns: Symbol, base: Symbol) = INTERNER.intern(Pair(ns, base))
    }

    override fun toString() = "$ns/$base"
}

class Keyword private constructor(override val base: Symbol) : Ident {
    companion object {
        private val INTERNER: Interner<Symbol, Keyword> = Interner(::Keyword)

        fun intern(sym: Symbol) = INTERNER.intern(sym)
    }

    override fun toString() = ":$base"
}

class QKeyword private constructor(override val ns: Symbol, override val base: Symbol) : QIdent {
    companion object {
        private val INTERNER: Interner<Pair<Symbol, Symbol>, QKeyword> = Interner { (ns, base) ->
            //            Regex(":(.+)/(.+)").matchEntire(it)!!
//                .groups
//                .let { groups ->
//                    QKeyword(Symbol.intern(groups[1]!!.value), Symbol.intern(groups[2]!!.value))
//                }
            QKeyword(ns, base)
        }

        fun intern(ns: Symbol, base: Symbol) = INTERNER.intern(Pair(ns, base))
    }

    override fun toString() = ":$ns/$base"
}

