package brj.runtime

import brj.BridjeLanguage
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeFunction(val callTarget: CallTarget) : TruffleObject {

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = "<BridjeFunction>"

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun execute(args: Array<Any>): Any = callTarget.call(*args)
}