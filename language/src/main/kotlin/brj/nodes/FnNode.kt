package brj.nodes

import brj.BridjeNode
import brj.analyser.CaptureSource
import brj.analyser.FrameSlotCapture
import brj.analyser.TransitiveCapture
import brj.runtime.BridjeFunction
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
    private val captureSources: Array<CaptureSource>,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any {
        val capturedValues = Array<Any?>(captureSources.size) { i ->
            when (val source = captureSources[i]) {
                is FrameSlotCapture -> frame.getObject(source.slot)
                is TransitiveCapture -> {
                    @Suppress("UNCHECKED_CAST")
                    val outerCaptures = frame.arguments[0] as Array<Any?>
                    outerCaptures[source.captureIndex]
                }
            }
        }
        return BridjeFunction(callTarget, capturedValues)
    }
}
