package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.strings.TruffleString

@ExportLibrary(InteropLibrary::class)
class BridjeTaggedTuple(
    val constructor: BridjeTagConstructor,
    val values: Array<Any>
) : TruffleObject {

    private val tagString: TruffleString = TruffleString.fromConstant(constructor.tag, TruffleString.Encoding.UTF_8)

    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = values.size.toLong()

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx >= 0 && idx < values.size

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (!isArrayElementReadable(idx)) throw InvalidArrayIndexException.create(idx)
        return values[idx.toInt()]
    }

    @ExportMessage
    fun hasMetaObject() = true

    @ExportMessage
    fun getMetaObject(): Any = constructor

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String {
        val interop = InteropLibrary.getUncached()
        return "${constructor.tag}(${values.joinToString(", ") { interop.toDisplayString(it) as String }})"
    }
}
