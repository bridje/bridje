package brj.runtime

import brj.BridjeLanguage
import brj.Typing
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
    fun readMember(key: String) = globalVars[symbol(key)]!!.bridjeVar.value

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") allowSideEffects: Boolean) = "BridjeEnv"

    @TruffleBoundary
    fun def(sym: Symbol, typing: Typing, value: Any) {
        CompilerAsserts.neverPartOfCompilation()
        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) {
                if (typing.res != globalVar.typing.res) TODO()
                globalVar.also { when(it) {
                    is DefVar -> it.bridjeVar.set(value)
                    is DefxVar -> it.defaultImpl.set(value)
                } }
            } else DefVar(sym, typing, BridjeVar(value))
        }
    }

    @TruffleBoundary
    fun defx(sym: Symbol, typing: Typing, value: BridjeFunction, defaultImplVar: BridjeVar) {
        CompilerAsserts.neverPartOfCompilation()
        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) TODO()
            else DefxVar(sym, typing, BridjeVar(value), defaultImplVar)
        }
    }
}