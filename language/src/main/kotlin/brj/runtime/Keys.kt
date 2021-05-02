package brj.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class Keys(val keys: Array<Any>) : TruffleObject {
    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = keys.size

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx >= 0 && idx < keys.size

    @ExportMessage
    fun readArrayElement(idx: Long) = keys[idx.toInt()]
}