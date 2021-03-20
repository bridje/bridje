package brj.runtime

import brj.BridjeLanguage
import brj.MonoType
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeEnv : TruffleObject {

    internal val globalVars = mutableMapOf<Symbol, GlobalVar>()

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) =
        BridjeVector(globalVars.keys.map { it.local }.toList().toTypedArray())

    @ExportMessage
    fun isMemberReadable(key: String) = globalVars.containsKey(symbol(key))

    @ExportMessage
    fun readMember(key: String) = globalVars[symbol(key)]!!.value

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") allowSideEffects: Boolean) = "BridjeEnv"

    @TruffleBoundary
    fun setVar(sym: Symbol, type: MonoType, value: Any?) {
        CompilerAsserts.neverPartOfCompilation()
        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) {
                type == globalVar.type || TODO()
                globalVar.also { it.value = value }
            } else GlobalVar(sym, type, value)
        }
    }
}