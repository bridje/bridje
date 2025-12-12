package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.source.SourceSection

class ExecuteArrayNode(
    @field:Children private val exprNodes: Array<BridjeNode>,
    loc: SourceSection? = null
) : BridjeNode(loc) {

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Array<Any> {
        val res = arrayOfNulls<Any>(exprNodes.size)
        for (i in exprNodes.indices) {
            res[i] = exprNodes[i].execute(frame)
        }
        @Suppress("UNCHECKED_CAST")
        return res as Array<Any>
    }
}
