package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.source.SourceSection

class LetNode(
    private val slot: Int,
    @field:Child private var bindingNode: BridjeNode,
    @field:Child private var bodyNode: BridjeNode,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any? {
        val value = bindingNode.execute(frame)
        frame.setObject(slot, value)
        return bodyNode.execute(frame)
    }
}
