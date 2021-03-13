package brj

import com.oracle.truffle.api.interop.TruffleObject

class Symbol private constructor(val local: String) : TruffleObject {
    companion object {
        private val SYMBOLS = mutableMapOf<String, Symbol>()
        internal fun symbol(local: String) = SYMBOLS.computeIfAbsent(local, ::Symbol)
    }

    override fun toString() = local
}

