package brj

private class Interner<K, V>(val f: (K) -> V) {
    val interned = mutableMapOf<K, V>()

    fun intern(k: K): V {
        return interned.getOrPut(k) { f(k) }
    }
}

class Symbol private constructor(val isKeyword: Boolean, val baseStr: String) {
    private val stringRep = "${if (isKeyword) ":" else ""}$baseStr"

    companion object {
        private val INTERNER: Interner<String, Symbol> = Interner {
            val groups = Regex("(:)?(.+)").matchEntire(it)!!.groups
            Symbol(isKeyword = groups[1] != null, baseStr = groups[2]!!.value.intern())
        }

        fun mkSym(str: String) = INTERNER.intern(str)
    }

    override fun toString() = stringRep
}

class QSymbol private constructor(val isKeyword: Boolean, val ns: Symbol, val base: Symbol) {
    private val stringRep = "${if (isKeyword) ":" else ""}$ns/$base"

    companion object {
        private val INTERNER: Interner<String, QSymbol> = Interner {
            val groups = Regex("(:)?(.+?)/(.+)").matchEntire(it)!!.groups
            QSymbol(isKeyword = groups[1] != null, ns = Symbol.mkSym(groups[2]!!.value), base = Symbol.mkSym(groups[3]!!.value))
        }

        fun mkQSym(str: String) = INTERNER.intern(str)
    }

    override fun toString() = stringRep
}

