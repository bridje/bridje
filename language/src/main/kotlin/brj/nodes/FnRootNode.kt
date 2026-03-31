package brj.nodes

import brj.BridjeNode
import brj.BridjeLanguage
import brj.runtime.BridjeRecord
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
        val argCount = args.size - paramStartIndex
        repeat(paramCount) { i ->
            frame.setObject(i, if (i < argCount) args[paramStartIndex + i] else BridjeRecord.EMPTY)
        }
        return bodyNode.execute(frame)
    }
}
