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
    @field:Child private var bodyNode: BridjeNode
) : RootNode(language, frameDescriptor) {

    override fun execute(frame: VirtualFrame): Any? {
        val args = frame.arguments
        repeat(paramCount) { frame.setObject(it, args[it]) }
        return bodyNode.execute(frame)
    }
}
