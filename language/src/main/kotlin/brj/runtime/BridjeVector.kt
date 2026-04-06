package brj.runtime

import brj.Form
import brj.runtime.Anomaly.Companion.incorrect
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeVector(val els: List<Any>, override val meta: BridjeRecord = BridjeRecord.EMPTY) : TruffleObject, Meta<BridjeVector> {

    companion object {
        @JvmStatic
        fun count(vec: BridjeVector): Long = vec.els.size.toLong()

        @JvmStatic
        fun first(vec: BridjeVector): Any {
            if (vec.els.isEmpty()) throw incorrect("first: empty collection")
            return vec.els[0]
        }

        @JvmStatic
        fun firstOrNull(vec: BridjeVector): Any? =
            if (vec.els.isEmpty()) BridjeNull else vec.els[0]

        @JvmStatic
        fun rest(vec: BridjeVector): BridjeVector = BridjeVector(vec.els.drop(1))

        @JvmStatic
        fun cons(element: Any, vec: BridjeVector): BridjeVector = BridjeVector(listOf(element) + vec.els)

        @JvmStatic
        fun isEmpty(vec: BridjeVector): Boolean = vec.els.isEmpty()

        @JvmStatic
        fun concat(vec1: BridjeVector, vec2: BridjeVector): BridjeVector = BridjeVector(vec1.els + vec2.els)
    }

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
