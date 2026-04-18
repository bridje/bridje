package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.strings.TruffleString

@ExportLibrary(InteropLibrary::class)
object LocMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Loc", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true

    @ExportMessage fun getMetaSimpleName(): Any = name

    @ExportMessage fun getMetaQualifiedName(): Any = name

    @ExportMessage fun isMetaInstance(instance: Any?) = instance is Loc

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Loc"
}

@ExportLibrary(InteropLibrary::class)
class Loc(val section: SourceSection) : TruffleObject {

    private val sourceName: TruffleString =
        TruffleString.fromConstant(section.source.name ?: "<unknown>", TruffleString.Encoding.UTF_8)

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = LocMeta

    @ExportMessage fun hasMembers() = true

    @ExportMessage
    fun getMembers(@Suppress("UNUSED_PARAMETER") includeInternal: Boolean): Any =
        BridjeRecord.Keys(MEMBERS)

    @ExportMessage
    fun isMemberReadable(member: String): Boolean = member in MEMBERS

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any = when (member) {
        "source" -> sourceName
        "startLine" -> section.startLine.toLong()
        "startColumn" -> section.startColumn.toLong()
        "endLine" -> section.endLine.toLong()
        "endColumn" -> section.endColumn.toLong()
        else -> throw UnknownIdentifierException.create(member)
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String =
        "Loc(${section.source.name ?: "<unknown>"}:${section.startLine}:${section.startColumn})"

    companion object {
        private val MEMBERS: Array<Any> =
            arrayOf("source", "startLine", "startColumn", "endLine", "endColumn")
    }
}
