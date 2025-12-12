package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class FnNode(
    private val function: BridjeFunction,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any = function
}
