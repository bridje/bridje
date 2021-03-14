package brj.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class Symbol private constructor(val local: String) : TruffleObject {
    companion object {
        private val SYMBOLS = mutableMapOf<String, Symbol>()
        internal fun symbol(local: String) = SYMBOLS.computeIfAbsent(local, ::Symbol)
    }

    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = local

    override fun toString() = local
}

