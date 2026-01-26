package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.`object`.Shape
import java.lang.invoke.MethodHandles

@ExportLibrary(InteropLibrary::class)
class BridjeRecord(shape: Shape) : DynamicObject(shape) {

    companion object {
        private val INTEROP = InteropLibrary.getUncached()
        private val OBJECT_LIBRARY = DynamicObjectLibrary.getUncached()

        val shape: Shape = Shape.newBuilder()
            .layout(BridjeRecord::class.java, MethodHandles.lookup())
            .build()
    }

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    fun getMembers(includeInternal: Boolean,
                   @CachedLibrary("this") objectLibrary: DynamicObjectLibrary): Any {
        return Keys(objectLibrary.getKeyArray(this))
    }

    @ExportMessage
    fun isMemberReadable(name: String,
                         @CachedLibrary("this") objectLibrary: DynamicObjectLibrary): Boolean {
        return objectLibrary.containsKey(this, name)
    }

    @ExportMessage
    @Throws(UnknownIdentifierException::class)
    fun readMember(name: String,
                   @CachedLibrary("this") objectLibrary: DynamicObjectLibrary): Any? {
        val value = objectLibrary.getOrDefault(this, name, null)
            ?: throw UnknownIdentifierException.create(name)
        return value
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String {
        val keys = OBJECT_LIBRARY.getKeyArray(this)
        return keys.joinToString(prefix = "{", separator = ", ", postfix = "}") { key ->
            val value = OBJECT_LIBRARY.getOrDefault(this, key, null)
            "$key ${INTEROP.toDisplayString(value)}"
        }
    }

    @ExportLibrary(InteropLibrary::class)
    class Keys(private val keys: Array<Any>) : TruffleObject {
        @ExportMessage
        fun hasArrayElements() = true

        @ExportMessage
        fun getArraySize() = keys.size.toLong()

        @ExportMessage
        fun isArrayElementReadable(idx: Long) = idx >= 0 && idx < keys.size

        @ExportMessage
        fun readArrayElement(idx: Long): Any = keys[idx.toInt()]
    }
}
