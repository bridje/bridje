package brj.runtime

import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.`object`.Shape
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

private val SHAPE = Shape.newBuilder().layout(BridjeObject::class.java).build()

@ExportLibrary(InteropLibrary::class)
class BridjeObject : DynamicObject(SHAPE) {
    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    fun getMembers(includeInternal: Boolean, @CachedLibrary("this") dynObj: DynamicObjectLibrary): TruffleObject =
        Keys(dynObj.getKeyArray(this))

    @ExportMessage
    fun isMemberReadable(member: String, @CachedLibrary("this") dynObj: DynamicObjectLibrary) =
        dynObj.containsKey(this, member)

    @ExportMessage
    fun readMember(member: String, @CachedLibrary("this") dynObj: DynamicObjectLibrary): Any =
        dynObj.getOrDefault(this, member, null)

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
}