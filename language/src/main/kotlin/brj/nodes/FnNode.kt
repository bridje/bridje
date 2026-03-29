package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeFunction
import brj.runtime.ClosureBridjeFunction
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class FnNode(
    private val function: BridjeFunction,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any = function
}

class ClosureFnNode(
    private val callTarget: RootCallTarget,
    private val captureSlots: IntArray,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any {
        val capturedValues = Array<Any?>(captureSlots.size) { i ->
            frame.getObject(captureSlots[i])
        }
        return ClosureBridjeFunction(callTarget, capturedValues)
    }
}
