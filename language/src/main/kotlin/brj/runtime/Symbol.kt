package brj.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class Symbol private constructor(val ns: Symbol?, val local: String) : TruffleObject {
    companion object {
        private val SYMBOLS = mutableMapOf<Pair<Symbol?, String>, Symbol>()

        private fun intern(ns: Symbol?, local: String) = SYMBOLS.computeIfAbsent(Pair(ns, local)) { Symbol(ns, local) }

        internal val String.sym get() = intern(null, this)

        internal val String.qsym : Symbol get() {
            val (ns, local) = split("/")
            return intern(ns.sym, local)
        }
    }

    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = toString()

    override fun toString() = if (ns != null) "$ns/$local" else local
}

