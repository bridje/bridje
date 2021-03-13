package brj

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeSet(val els: Array<Any?>) : TruffleObject {
    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = els.size

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx >= 0 && idx < els.size

    @ExportMessage
    fun readArrayElement(idx: Long) = els[idx.toInt()]

    companion object {
        private val interopLibrary = InteropLibrary.getUncached()
    }

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean) =
        "#{${els.map(interopLibrary::toDisplayString).joinToString(", ")}}"
}