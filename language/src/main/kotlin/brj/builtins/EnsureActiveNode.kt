package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import brj.runtime.BridjeNull
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class EnsureActiveNode(language: BridjeLanguage) : RootNode(language) {

    override fun execute(frame: VirtualFrame): Any {
        return doCheck()
    }

    @TruffleBoundary
    private fun doCheck(): Any {
        if (Thread.interrupted()) {
            throw BridjeContext.get(this).interruptedException("Task was interrupted")
        }
        return BridjeNull
    }
}
