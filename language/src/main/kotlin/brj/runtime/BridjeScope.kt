package brj.runtime

import brj.BridjeLanguage
import brj.NsEnv
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeScope(private val context: BridjeContext) : TruffleObject {
    private val namespaces: Map<String, NsEnv>
        get() = context.namespaces

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage(): Class<BridjeLanguage> = BridjeLanguage::class.java

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) =
        BridjeRecord.Keys(namespaces.keys.toTypedArray())

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String) = member in namespaces

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String) =
        namespaces[member] ?: throw UnknownIdentifierException.create(member)

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean) = "bridje"
}
