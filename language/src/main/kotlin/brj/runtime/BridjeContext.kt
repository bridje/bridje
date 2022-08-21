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

    internal val userNsContext = NsContext(this, USER)
    internal val coreNsContext = NsContext(this, BRJ_CORE)
    internal val nses = mutableMapOf(USER to userNsContext, BRJ_CORE to coreNsContext)

    internal val imports = mutableMapOf<Symbol, TruffleObject>()

    internal val interop = InteropLibrary.getUncached()

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) =
        BridjeVector(nses.keys.map { it.local }.toList().toTypedArray())

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
    fun importClass(className: Symbol) {
        val clazz = truffleEnv.lookupHostSymbol(className.local) as TruffleObject
        val simpleClassName = interop.asString(interop.getMetaSimpleName(clazz)).sym
        imports[simpleClassName] = clazz
    }

    @TruffleBoundary
    fun poly(lang: String, code: String): Any =
        truffleEnv.parsePublic(Source.newBuilder(lang, code, "<brj-inline>").build()).call()

    internal operator fun get(ns: Symbol) = nses[ns]

    internal operator fun set(ns: Symbol, nsCtx: NsContext): NsContext {
        nses[ns] = nsCtx
        return nsCtx
    }
}