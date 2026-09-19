package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.strings.TruffleString

/**
 * Canonical [QSymbol] for `.loc` source-location metadata — declared in
 * `brj.rdr`, written into the meta records produced by the analyser and the
 * reader. A single shared instance avoids per-form allocation on `form.meta`.
 */
val LOC_KEY: QSymbol = QSymbol("brj.rdr".sym, "loc".sym)

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
class Loc(val section: SourceSection) : TruffleObject, BridjeObject {

    private val sourceName: TruffleString =
        TruffleString.fromConstant(section.source.name ?: "<unknown>", TruffleString.Encoding.UTF_8)

    private val sourcePath: TruffleString? =
        section.source.path?.let { TruffleString.fromConstant(it, TruffleString.Encoding.UTF_8) }

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
    fun readMember(member: String): Any = readByName(member)

    override fun hasKey(key: BridjeKey): Boolean =
        key.ns === READER_NS && key.name.name in MEMBERS

    @TruffleBoundary
    override fun readKey(key: BridjeKey): Any {
        if (key.ns !== READER_NS) throw UnknownIdentifierException.create(key.sym.toString())
        return readByName(key.name.name)
    }

    private fun readByName(member: String): Any = when (member) {
        "source" -> sourceName
        "path" -> sourcePath ?: BridjeNull
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
        private val READER_NS = "brj.rdr".sym
        private val MEMBERS: Array<String> =
            arrayOf("source", "path", "startLine", "startColumn", "endLine", "endColumn")
    }
}
