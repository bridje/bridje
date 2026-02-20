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

@ExportLibrary(InteropLibrary::class)
class BridjeRecord internal constructor(
    @JvmField internal val storage: DynamicObject = Storage(SHAPE),
    private val _meta: BridjeRecord? = null // nullable to avoid circular initialization with EMPTY
) : TruffleObject, Meta<BridjeRecord> {

    override val meta: BridjeRecord get() = _meta ?: EMPTY

    internal constructor(keys: Array<String>, values: Array<Any>) : this(
        Storage(SHAPE).also { storage ->
            for (i in keys.indices) {
                OBJECT_LIBRARY.put(storage, keys[i], values[i])
            }
        }
    )

    override fun withMeta(newMeta: BridjeRecord?): BridjeRecord =
        BridjeRecord(storage, newMeta)

    internal fun put(key: Any, value: Any?): BridjeRecord {
        val newStorage = Storage(SHAPE)
        for (k in OBJECT_LIBRARY.getKeyArray(storage)) {
            OBJECT_LIBRARY.put(newStorage, k, OBJECT_LIBRARY.getOrDefault(storage, k, null))
        }
        OBJECT_LIBRARY.put(newStorage, key, value)
        return BridjeRecord(newStorage, meta)
    }

    internal fun set(key: Any, value: Any?): Any? {
        val old = OBJECT_LIBRARY.getOrDefault(storage, key, null)
        OBJECT_LIBRARY.put(storage, key, value)
        return old
    }

    private class Storage(shape: Shape) : DynamicObject(shape)

    companion object {
        private val INTEROP = InteropLibrary.getUncached()
        private val OBJECT_LIBRARY = DynamicObjectLibrary.getUncached()

        private val SHAPE: Shape = Shape.newBuilder().build()

        val EMPTY: BridjeRecord = BridjeRecord()
    }

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    fun getMembers(includeInternal: Boolean,
                   @CachedLibrary("this.storage") objectLibrary: DynamicObjectLibrary): Any {
        return Keys(objectLibrary.getKeyArray(storage))
    }

    @ExportMessage
    fun isMemberReadable(name: String,
                         @CachedLibrary("this.storage") objectLibrary: DynamicObjectLibrary): Boolean {
        return objectLibrary.containsKey(storage, name)
    }

    @ExportMessage
    @Throws(UnknownIdentifierException::class)
    fun readMember(name: String,
                   @CachedLibrary("this.storage") objectLibrary: DynamicObjectLibrary): Any? {
        val value = objectLibrary.getOrDefault(storage, name, null)
            ?: throw UnknownIdentifierException.create(name)
        return value
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String {
        val keys = OBJECT_LIBRARY.getKeyArray(storage)
        return keys.joinToString(prefix = "{", separator = ", ", postfix = "}") { key ->
            val value = OBJECT_LIBRARY.getOrDefault(storage, key, null)
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
