package brj.runtime

import brj.Form
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeVector(val els: Array<Any>, override val meta: BridjeRecord = BridjeRecord.EMPTY) : TruffleObject, Meta<BridjeVector> {

    override fun withMeta(newMeta: BridjeRecord?): BridjeVector = BridjeVector(els, newMeta ?: BridjeRecord.EMPTY)

    fun toFormList(): List<Form> = els.map { it as Form }

    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = els.size.toLong()

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx >= 0 && idx < els.size

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (!isArrayElementReadable(idx)) throw InvalidArrayIndexException.create(idx)
        return els[idx.toInt()]
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String {
        val interop = InteropLibrary.getUncached()
        return "[${els.joinToString(" ") { interop.toDisplayString(it) as String }}]"
    }
}
