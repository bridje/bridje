package brj.builtins

import brj.BridjeLanguage
import brj.runtime.Anomaly.Companion.interrupted
import brj.runtime.BridjeNull
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class SleepMsNode(language: BridjeLanguage) : RootNode(language) {

    override fun execute(frame: VirtualFrame): Any {
        val ms = frame.arguments[0] as Long
        return doSleep(ms)
    }

    @TruffleBoundary
    private fun doSleep(ms: Long): Any {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            throw interrupted("Sleep was interrupted", e)
        }
        return BridjeNull
    }
}
