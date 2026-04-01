package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.Node

@ExportLibrary(InteropLibrary::class)
class BridjeException(val anomalyValue: Any, node: Node? = null) : AbstractTruffleException(null, extractCause(anomalyValue), UNLIMITED_STACK_TRACE, node) {

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
    fun getExceptionMessage(): String {
        val msg = readDataMember(anomalyValue, "exnMessage")
        if (msg != null) return InteropLibrary.getUncached().asString(msg)
        return InteropLibrary.getUncached().toDisplayString(anomalyValue).toString()
    }

    @ExportMessage
    @TruffleBoundary
    fun hasExceptionCause(): Boolean = extractCause(anomalyValue) != null

    @ExportMessage
    @TruffleBoundary
    fun getExceptionCause(): AbstractTruffleException {
        val cause = extractCause(anomalyValue)
            ?: throw UnsupportedOperationException("No exception cause")
        return if (cause is AbstractTruffleException) cause
        else throw UnsupportedOperationException("Cause is not a guest exception")
    }

    companion object {
        @TruffleBoundary
        private fun readDataMember(anomalyValue: Any, memberName: String): Any? {
            val interop = InteropLibrary.getUncached()
            if (!interop.hasArrayElements(anomalyValue)) return null
            val data = try { interop.readArrayElement(anomalyValue, 0) } catch (_: Exception) { return null }
            if (!interop.isMemberReadable(data, memberName)) return null
            return try { interop.readMember(data, memberName) } catch (_: Exception) { null }
        }

        @TruffleBoundary
        private fun extractCause(anomalyValue: Any): Throwable? {
            val cause = readDataMember(anomalyValue, "exnCause") ?: return null
            return cause as? Throwable
        }
    }
}
