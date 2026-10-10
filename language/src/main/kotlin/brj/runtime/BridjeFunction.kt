package brj.runtime

import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeFunction(
    private val callTarget: RootCallTarget,
    private val arity: Int,
    private val capturedValues: Array<Any?>? = null,
) : TruffleObject {

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    @Throws(ArityException::class)
    fun execute(arguments: Array<Any?>): Any? {
        val args = arguments.fitted(arity)
        return if (capturedValues != null) callTarget.call(capturedValues, *args) else callTarget.call(*args)
    }
}
