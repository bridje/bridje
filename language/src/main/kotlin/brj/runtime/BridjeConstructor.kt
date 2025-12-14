package brj.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeConstructor(
    private val hostClass: TruffleObject
) : TruffleObject {

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun execute(arguments: Array<Any?>): Any? {
        return InteropLibrary.getUncached().instantiate(hostClass, *arguments)
    }

    @ExportMessage
    fun isInstantiable() = true

    @ExportMessage
    fun instantiate(arguments: Array<Any?>): Any? {
        return InteropLibrary.getUncached().instantiate(hostClass, *arguments)
    }
}
