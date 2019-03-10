package brj

import brj.SymbolType.*

private class Interner<K, V>(val f: (K) -> V) {
    val interned = mutableMapOf<K, V>()

    fun intern(k: K): V {
        return interned.getOrPut(k) { f(k) }
    }
}

internal enum class SymbolType {
    VAR_SYM, RECORD_KEY_SYM, VARIANT_KEY_SYM, TYPE_ALIAS_SYM, POLYVAR_SYM
}

private fun symbolType(sym: Symbol): SymbolType {
    val firstIsUpper = sym.baseStr.first().isUpperCase()
    val firstIsDot = sym.baseStr.first() == '.'

    return if (sym.isKeyword) {
        if (firstIsUpper) VARIANT_KEY_SYM else RECORD_KEY_SYM
    } else {
        if (firstIsUpper) TYPE_ALIAS_SYM else if (firstIsDot) POLYVAR_SYM else VAR_SYM
    }
}

sealed class Ident {
    internal abstract val symbolType: SymbolType
}

class Symbol private constructor(val isKeyword: Boolean, val baseStr: String) : Ident() {
    private val stringRep = "${if (isKeyword) ":" else ""}$baseStr"
    override val symbolType = brj.symbolType(this)

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
    override val symbolType = base.symbolType
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

