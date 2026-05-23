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
        BridjeRecord.Keys(Array(constructor.fieldNames.size) { constructor.fieldNames[it].toString() })

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String): Boolean = resolvePolyglotMember(member) >= 0

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any {
        val idx = resolvePolyglotMember(member)
        if (idx < 0) throw UnknownIdentifierException.create(member)
        if (idx == AMBIGUOUS)
            throw Anomaly.incorrect("ambiguous polyglot member '$member' — multiple namespaces define this key on this tag")
        return values[idx]
    }

    private fun resolvePolyglotMember(member: String): Int {
        val parsed = QSymbol.parse(member)
        if (parsed != null) return constructor.fieldIndices[parsed] ?: -1
        // Unqualified — single match required.
        val unq = Symbol.intern(member)
        var idx = -1
        for ((field, i) in constructor.fieldIndices) {
            if (field.name === unq) {
                if (idx >= 0) return AMBIGUOUS
                idx = i
            }
        }
        return idx
    }

    override fun hasKey(key: BridjeKey): Boolean = key.sym in constructor.fieldIndices

    @TruffleBoundary
    override fun readKey(key: BridjeKey): Any {
        val idx = constructor.fieldIndices[key.sym]
            ?: throw UnknownIdentifierException.create(key.sym.toString())
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

    private companion object {
        private const val AMBIGUOUS = -2
    }
}
