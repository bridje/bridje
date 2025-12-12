package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop

class DoNode(
    @field:Children private val sideEffectNodes: Array<BridjeNode>,
    @field:Child private var resultNode: BridjeNode
) : BridjeNode() {

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        for (node in sideEffectNodes) {
            node.execute(frame)
        }
        return resultNode.execute(frame)
    }
}
