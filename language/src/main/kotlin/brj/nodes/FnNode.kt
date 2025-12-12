package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.frame.VirtualFrame

class FnNode(
    private val function: BridjeFunction
) : BridjeNode() {
    override fun execute(frame: VirtualFrame): Any = function
}
