package brj

class Symbol private constructor(val name: String) {
    companion object {
        private val SYMBOLS: MutableMap<String, Symbol> = mutableMapOf()

        fun create(name: String): Symbol = SYMBOLS.getOrPut(name) { Symbol(name) }
    }

    override fun toString() = name
}

class NamespacedSymbol private constructor(val ns: Symbol, val name: Symbol) {
    companion object {
        private val SYMBOLS: MutableMap<Pair<Symbol, Symbol>, NamespacedSymbol> = mutableMapOf()
        fun create(ns: Symbol, name: Symbol) = SYMBOLS.getOrPut(Pair(ns, name)) { NamespacedSymbol(ns, name) }
    }

    override fun toString() = "$ns/$name"
}

data class Keyword(val name: String) {
    override fun toString(): String = ":$name"
}

data class NamespacedKeyword(val ns: Symbol, val name: Symbol) {
    override fun toString(): String = ":$ns/$name"
}
