package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.Node

@ExportLibrary(InteropLibrary::class)
class BridjeException(val anomalyValue: Any, node: Node? = null) : AbstractTruffleException(null, null, UNLIMITED_STACK_TRACE, node) {

    @ExportMessage
    fun isException(): Boolean = true

    @ExportMessage
    fun throwException(): RuntimeException = throw this

    @ExportMessage
    fun getExceptionType(): ExceptionType = ExceptionType.RUNTIME_ERROR

    @ExportMessage
    fun hasExceptionMessage(): Boolean = true

    @ExportMessage
    @TruffleBoundary
    fun getExceptionMessage(): String =
        InteropLibrary.getUncached().toDisplayString(anomalyValue).toString()
}
