package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeFuture
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class AwaitNode(language: BridjeLanguage) : RootNode(language) {

    override fun execute(frame: VirtualFrame): Any? {
        val deferred = frame.arguments[0] as BridjeFuture
        return doAwait(deferred)
    }

    @TruffleBoundary
    private fun doAwait(deferred: BridjeFuture): Any? = deferred.get()
}
