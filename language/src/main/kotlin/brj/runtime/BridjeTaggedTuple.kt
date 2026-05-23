package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeTaggedTuple(
    val constructor: BridjeTagConstructor,
    val values: Array<Any>
) : TruffleObject, BridjeObject {

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
    fun hasMembers() = constructor.fieldNames.isNotEmpty()

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean): Any =
        BridjeRecord.Keys(constructor.fieldNames.toTypedArray())

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String) = Symbol.intern(member) in constructor.fieldIndices

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any {
        val idx = constructor.fieldIndices[Symbol.intern(member)]
            ?: throw UnknownIdentifierException.create(member)
        return values[idx]
    }

    override fun hasKey(key: BridjeKey): Boolean = key.name in constructor.fieldIndices

    @TruffleBoundary
    override fun readKey(key: BridjeKey): Any {
        val idx = constructor.fieldIndices[key.name]
            ?: throw UnknownIdentifierException.create(key.name.name)
        return values[idx]
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
