package brj.runtime

import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class Symbol private constructor(val name: String) : TruffleObject {
    companion object {
        private val SYMBOLS = mutableMapOf<String, Symbol>()

        private fun intern(local: String) = SYMBOLS.computeIfAbsent(local) { Symbol(local) }

        internal val String.sym get() = intern(this)
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = toString()

    override fun toString() = name
}

@ExportLibrary(InteropLibrary::class)
class QSymbol private constructor(val ns: Symbol, val local: Symbol): TruffleObject {
    companion object {
        private val SYMBOLS = mutableMapOf<Pair<Symbol, Symbol>, QSymbol>()

        private fun intern(ns: Symbol, local: Symbol) = SYMBOLS.computeIfAbsent(Pair(ns, local)) { QSymbol(ns, local) }

        internal val String.qsym : QSymbol get() {
            val (ns, local) = split("/")
            return intern(ns.sym, local.sym)
        }

        internal val Pair<Symbol, Symbol>.qsym get() = intern(first, second)
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = toString()

    override fun toString() = "$ns/$local"
}