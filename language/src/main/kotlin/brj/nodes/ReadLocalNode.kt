package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame

class ReadLocalNode(private val slot: Int) : BridjeNode() {
    override fun execute(frame: VirtualFrame): Any {
        return frame.getObject(slot)
    }
}
