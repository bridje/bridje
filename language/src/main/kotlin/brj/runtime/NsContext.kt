package brj.runtime

import brj.BridjeLanguage
import brj.Typing
import brj.nodes.DefxRootNodeGen
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
internal class NsContext(
    val ctx: BridjeContext, val ns: Symbol,
    val aliases: Map<Symbol, NsContext> = emptyMap(),
    val refers: Map<Symbol, GlobalVar> = emptyMap(),
    val imports: Map<Symbol, TruffleObject> = emptyMap()
) : TruffleObject {

    internal val globalVars = mutableMapOf<Symbol, GlobalVar>()

    @ExportMessage
    fun hasMembers() = true

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun getMembers(includeInternal: Boolean) =
        BridjeVector(globalVars.keys.map { it.local }.toList().toTypedArray())

    @ExportMessage
    fun isMemberReadable(key: String) = globalVars.containsKey(key.sym)

    @ExportMessage
    fun readMember(key: String) = globalVars[key.sym]!!.bridjeVar.value

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    @Suppress("UNUSED_PARAMETER")
    fun toDisplayString(allowSideEffects: Boolean) = "BridjeNsEnv[$ns]"

    @CompilerDirectives.TruffleBoundary
    fun def(sym: Symbol, typing: Typing, value: Any) {
        CompilerAsserts.neverPartOfCompilation()
        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) {
//                if (typing.res != globalVar.typing.res) TODO()
                globalVar.also {
                    when (it) {
                        is DefVar -> it.bridjeVar.set(value)
                        is DefxVar -> it.defaultImpl.set(value)
                    }
                }
            } else DefVar(sym, typing, BridjeVar(value))
        }
    }

    @CompilerDirectives.TruffleBoundary
    fun defx(sym: Symbol, typing: Typing) {
        CompilerAsserts.neverPartOfCompilation()

        val defaultImplVar = BridjeVar(null)

        val value = BridjeFunction(
            DefxRootNodeGen.DefxValueRootNodeGen.create(ctx.lang, FrameDescriptor(), sym, defaultImplVar).callTarget
        )

        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) TODO("global var already exists in `defx`, '$sym'")
            else DefxVar(sym, typing, BridjeVar(value), defaultImplVar)
        }
    }
}