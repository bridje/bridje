package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeVector
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.source.SourceSection

class VectorNode(
    @field:Child private var els: ExecuteCollNode,
    loc: SourceSection? = null
) : BridjeNode(loc) {

    override fun execute(frame: VirtualFrame): Any = BridjeVector(els.execute(frame))
}
