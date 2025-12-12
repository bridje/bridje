package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class ReadLocalNode(private val slot: Int, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any {
        return frame.getObject(slot)
    }
}
