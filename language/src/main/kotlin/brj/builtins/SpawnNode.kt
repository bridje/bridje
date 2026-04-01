package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import brj.runtime.BridjeFuture
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.RootNode

class SpawnNode(language: BridjeLanguage) : RootNode(language) {

    override fun execute(frame: VirtualFrame): Any {
        val fn = frame.arguments[0]
        return doSpawn(fn)
    }

    @TruffleBoundary
    private fun doSpawn(fn: Any): BridjeFuture {
        val interop = InteropLibrary.getUncached()
        val ctx = BridjeContext.get(this)
        val future = ctx.executor.submit<Any?> { interop.execute(fn) }
        return BridjeFuture(future, ctx.interruptedCtor)
    }
}
