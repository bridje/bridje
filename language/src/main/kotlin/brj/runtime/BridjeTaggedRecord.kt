package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeTaggedRecord(
    val constructor: BridjeTagConstructor,
    override val payload: BridjeRecord,
) : TruffleObject, Tagged {

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean): Any = INTEROP.getMembers(payload, includeInternal)

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String): Boolean = INTEROP.isMemberReadable(payload, member)

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any = INTEROP.readMember(payload, member)

    @ExportMessage
    fun hasMetaObject() = true

    @ExportMessage
    fun getMetaObject(): Any = constructor

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String =
        "${constructor.tag}${INTEROP.toDisplayString(payload)}"

    private companion object {
        private val INTEROP = InteropLibrary.getUncached()
    }
}
