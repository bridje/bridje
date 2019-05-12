package brj

import brj.SymbolKind.*

private class Interner<K, V>(val f: (K) -> V) {
    val interned = mutableMapOf<K, V>()

    fun intern(k: K): V {
        return interned.getOrPut(k) { f(k) }
    }
}

internal enum class SymbolKind {
    VAR_SYM, RECORD_KEY_SYM, VARIANT_KEY_SYM, TYPE_ALIAS_SYM, POLYVAR_SYM
}

private fun symbolKind(sym: Symbol): SymbolKind {
    val firstIsUpper = sym.baseStr.first().isUpperCase()

    return if (sym.isKeyword) {
        if (firstIsUpper) VARIANT_KEY_SYM else RECORD_KEY_SYM
    } else {
        when {
            firstIsUpper -> TYPE_ALIAS_SYM
            sym.baseStr.first() == '.' -> POLYVAR_SYM
            else -> VAR_SYM
        }
    }
}

sealed class Ident {
    internal abstract val symbolKind: SymbolKind
}

class Symbol private constructor(val isKeyword: Boolean, val baseStr: String) : Ident() {
    private val stringRep = "${if (isKeyword) ":" else ""}$baseStr"
    override val symbolKind = symbolKind(this)

    companion object {
        private val INTERNER: Interner<String, Symbol> = Interner {
            val groups = Regex("(:)?(.+)").matchEntire(it)!!.groups
            Symbol(isKeyword = groups[1] != null, baseStr = groups[2]!!.value.intern())
        }

        fun mkSym(str: String) = INTERNER.intern(str)
    }

    override fun toString() = stringRep
}

class QSymbol private constructor(val ns: Symbol, val base: Symbol) : Ident() {
    override val symbolKind = base.symbolKind
    private val stringRep = "${if (base.isKeyword) ":" else ""}$ns/${base.baseStr}"

    companion object {
        private val INTERNER: Interner<Pair<Symbol, Symbol>, QSymbol> = Interner { (ns, base) -> QSymbol(ns, base) }

        fun mkQSym(str: String): QSymbol {
            val groups = Regex("(:)?(.+?)/(.+)").matchEntire(str)!!.groups
            return mkQSym(ns = Symbol.mkSym(groups[2]!!.value), base = Symbol.mkSym("${if (groups[1] != null) ":" else ""}${groups[3]!!.value}"))
        }

        fun mkQSym(ns: Symbol, base: Symbol) = INTERNER.intern(Pair(ns, base))
    }

    override fun toString() = stringRep
}

