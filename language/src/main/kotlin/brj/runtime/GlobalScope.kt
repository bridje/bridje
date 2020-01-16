package brj.runtime

import brj.BridjeContext
import brj.emitter.BridjeObject
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.*
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
internal class GlobalScope(val ctx: BridjeContext) : BridjeObject {
    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) = ctx.truffleEnv.asGuestValue(ctx.env.nses.values.flatMap { it.vars.values }.associateBy { it.sym.toString() })

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(k: String): Boolean {
        val qsym = QSymbol(k)
        return ctx.env.nses[qsym.ns]?.vars?.containsKey(qsym.local) ?: false
    }

    @ExportMessage
    @TruffleBoundary
    fun readMember(k: String): Any? {
        val qsym = QSymbol(k)
        return ctx.env.nses[qsym.ns]?.vars?.get(qsym.local)?.value
    }
}