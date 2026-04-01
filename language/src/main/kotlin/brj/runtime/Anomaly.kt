package brj.runtime

import brj.runtime.Anomaly.AnomalyMeta.*
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.strings.TruffleString

@ExportLibrary(InteropLibrary::class)
class Anomaly(
    val meta: AnomalyMeta,
    @JvmField val data: BridjeRecord,
    cause: Throwable? = null,
    node: Node? = null
) : AbstractTruffleException(null, cause, UNLIMITED_STACK_TRACE, node) {

    @ExportLibrary(InteropLibrary::class)
    enum class AnomalyMeta(val tag: String) : TruffleObject {
        UNAVAILABLE("Unavailable"),
        INTERRUPTED("Interrupted"),
        BUSY("Busy"),
        INCORRECT("Incorrect"),
        FORBIDDEN("Forbidden"),
        UNSUPPORTED("Unsupported"),
        NOT_FOUND("NotFound"),
        CONFLICT("Conflict"),
        FAULT("Fault");

        private val tagString = TruffleString.fromConstant(tag, TruffleString.Encoding.UTF_8)

        @ExportMessage
        fun isMetaObject() = true

        @ExportMessage
        fun getMetaSimpleName(): Any = tagString

        @ExportMessage
        fun getMetaQualifiedName(): Any = tagString

        @ExportMessage
        fun isMetaInstance(instance: Any?) = instance is Anomaly && instance.meta == this

        @ExportMessage
        fun isExecutable() = true

        @ExportMessage
        fun isInstantiable() = true

        @ExportMessage
        @Throws(ArityException::class)
        fun execute(arguments: Array<Any?>): Any {
            if (arguments.size != 1)
                throw ArityException.create(1, 1, arguments.size)

            val data = arguments[0] as BridjeRecord

            return Anomaly(this, data)
        }

        @ExportMessage
        @Throws(ArityException::class)
        fun instantiate(arguments: Array<Any?>): Any = execute(arguments)

        @Suppress("UNUSED_PARAMETER")
        @ExportMessage
        @TruffleBoundary
        fun toDisplayString(allowSideEffects: Boolean): String = tag
    }

    // Exception interop

    @ExportMessage
    fun isException() = true

    @ExportMessage
    fun throwException(): RuntimeException = throw this

    @ExportMessage
    fun getExceptionType(): ExceptionType =
        if (meta == INTERRUPTED) ExceptionType.INTERRUPT
        else ExceptionType.RUNTIME_ERROR

    @ExportMessage
    fun hasExceptionMessage() = true

    @ExportMessage
    fun getExceptionMessage(@CachedLibrary("this.data") interop: InteropLibrary): String =
        if (interop.isMemberReadable(data, "exnMessage"))
            interop.readMember(data, "exnMessage").toString()
        else toDisplayString(false, interop)

    @ExportMessage
    fun hasExceptionCause(@CachedLibrary("this.data") interop: InteropLibrary): Boolean {
        if (!interop.isMemberReadable(data, "exnCause")) return false
        return interop.readMember(data, "exnCause") is Throwable
    }

    @ExportMessage
    fun getExceptionCause(@CachedLibrary("this.data") interop: InteropLibrary): AbstractTruffleException {
        val cause = interop.readMember(data, "exnCause")
        return cause as? AbstractTruffleException
            ?: throw UnsupportedOperationException("Cause is not a guest exception")
    }

    // Value interop (tagged tuple shape: one element, the data record)

    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = 1L

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return data
    }

    @ExportMessage
    fun hasMetaObject() = true

    @ExportMessage
    fun getMetaObject(): Any = meta

    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean, @CachedLibrary("this.data") interop: InteropLibrary): String =
        "${meta.tag}(${interop.toDisplayString(data)})"

    companion object {
        fun incorrect(message: String, node: Node? = null) =
            Anomaly(INCORRECT, BridjeRecord.EMPTY.put("exnMessage", message), node = node)

        fun interrupted(message: String, cause: Throwable? = null) =
            Anomaly(INTERRUPTED, BridjeRecord.EMPTY.put("exnMessage", message), cause = cause)

        fun fault(message: String, cause: Throwable? = null) =
            Anomaly(FAULT, BridjeRecord.EMPTY.put("exnMessage", message), cause = cause)
    }
}
