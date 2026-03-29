package brj.runtime

import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeFunction(
    private val callTarget: RootCallTarget
) : TruffleObject {

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun execute(arguments: Array<Any?>): Any? = callTarget.call(*arguments)
}

@ExportLibrary(InteropLibrary::class)
class ClosureBridjeFunction(
    private val callTarget: RootCallTarget,
    private val capturedValues: Array<Any?>
) : TruffleObject {

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun execute(arguments: Array<Any?>): Any? {
        val allArgs = arrayOfNulls<Any>(capturedValues.size + arguments.size)
        capturedValues.copyInto(allArgs)
        arguments.copyInto(allArgs, capturedValues.size)
        return callTarget.call(*allArgs)
    }
}
