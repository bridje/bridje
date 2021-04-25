package brj.runtime

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.`object`.Shape
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeRecord : DynamicObject(SHAPE) {
    companion object {
        private val SHAPE = Shape.newBuilder().layout(BridjeRecord::class.java).build()
    }

    private val interop = InteropLibrary.getUncached()

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean, @CachedLibrary("this") dynObj: DynamicObjectLibrary) =
        dynObj.getKeyArray(this)
            .joinToString(prefix = "{", separator = ", ", postfix = "}") { k ->
                val kStr = interop.toDisplayString(k, allowSideEffects)
                val v = dynObj.getOrDefault(this, k, null)
                val vStr = interop.toDisplayString(v, allowSideEffects)

                ":$kStr $vStr"
            }

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
        dynObj.getOrDefault(this, member, Nil)

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