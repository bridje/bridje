package brj.runtime

import brj.BridjeLanguage
import brj.Form
import brj.nodes.EvalRootNodeGen
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleContext
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.Source

internal val USER = "user".sym
internal val BRJ_CORE = "brj.core".sym
internal fun <R> TruffleContext.inContext(f: TruffleContext.() -> R): R {
    val prev = enter(null)
    return try {
        f()
    } finally {
        leave(null, prev)
    }
}

@ExportLibrary(InteropLibrary::class)
class BridjeContext(internal val lang: BridjeLanguage, internal val truffleEnv: TruffleLanguage.Env) : TruffleObject {

    internal var nses = mapOf(
        USER to NsContext(this, USER),
        BRJ_CORE to NsContext(this, BRJ_CORE))

    internal operator fun get(ns: Symbol) = nses[ns]

    private var currentNs = USER

    internal val currentNsContext get() = nses[currentNs]!!
    internal val coreNsContext get() = nses[BRJ_CORE]!!

    internal val interop = InteropLibrary.getUncached()

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(@Suppress("UNUSED_PARAMETER") includeInternal: Boolean) =
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

    fun <R> inNs(ns: Symbol, f: () -> R): R {
        val originalNs = currentNs
        try {
            currentNs = ns
            return f()
        } finally {
            currentNs = originalNs
        }
    }

    @TruffleBoundary
    fun evalForms(forms: List<Form>): Any? {
        val rootNode = EvalRootNodeGen.create(lang, forms)

        return truffleEnv.context.inContext {
            rootNode.callTarget.call()
        }
    }
}
