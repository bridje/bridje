package brj.nodes

import brj.BridjeNode
import brj.BridjeLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class FnRootNode(
    language: BridjeLanguage,
    frameDescriptor: FrameDescriptor,
    private val paramCount: Int,
    private val hasCapturedValues: Boolean = false,
    @field:Child private var bodyNode: BridjeNode
) : RootNode(language, frameDescriptor) {

    override fun execute(frame: VirtualFrame): Any? {
        val args = frame.arguments
        val paramStartIndex = if (hasCapturedValues) 1 else 0
        repeat(paramCount) { frame.setObject(it, args[paramStartIndex + it]) }
        return bodyNode.execute(frame)
    }
}
