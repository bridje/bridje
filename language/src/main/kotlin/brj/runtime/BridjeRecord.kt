package brj.runtime

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.`object`.Shape

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

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    fun getMembers(includeInternal: Boolean, @CachedLibrary("this") dynObj: DynamicObjectLibrary): TruffleObject =
        Keys(dynObj.getKeyArray(this))

    @ExportMessage
    fun isMemberReadable(member: String, @CachedLibrary("this") dynObj: DynamicObjectLibrary) =
        dynObj.containsKey(this, member)

    @ExportMessage
    fun readMember(member: String, @CachedLibrary("this") dynObj: DynamicObjectLibrary): Any =
        dynObj.getOrDefault(this, member, Nil)

}