package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.source.SourceSection

class ExecuteCollNode(
    @field:Children private val exprNodes: Array<BridjeNode>,
    loc: SourceSection? = null
) : BridjeNode(loc) {

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): List<Any> {
        val res = ArrayList<Any>(exprNodes.size)
        for (node in exprNodes) {
            res.add(node.execute(frame)!!)
        }
        return res
    }
}
