package brj.runtime

import brj.BridjeLanguage
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.Source

internal val USER = "user".sym
internal val BRJ_CORE = "brj.core".sym

@ExportLibrary(InteropLibrary::class)
class BridjeContext(internal val lang: BridjeLanguage, internal val truffleEnv: TruffleLanguage.Env) : TruffleObject {

    internal var nses = mapOf(
        USER to NsContext(this, USER),
        BRJ_CORE to NsContext(this, BRJ_CORE))

    private var currentNs = USER

    internal val currentNsContext get() = nses[currentNs]!!

    internal val userNsContext get() = nses[USER]!!
    internal val coreNsContext get() = nses[BRJ_CORE]!!

    internal val interop = InteropLibrary.getUncached()

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) =
        BridjeVector(nses.keys.map { it.name }.toList().toTypedArray())

    @ExportMessage
    fun isMemberReadable(key: String) = nses.containsKey(key.sym)

    @ExportMessage
    fun readMember(key: String): TruffleObject? = nses[key.sym]

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") allowSideEffects: Boolean) = "BridjeEnv"

    @TruffleBoundary
    fun poly(lang: String, code: String): Any =
        truffleEnv.parsePublic(Source.newBuilder(lang, code, "<brj-inline>").build()).call()

    internal operator fun get(ns: Symbol) = nses[ns]
}