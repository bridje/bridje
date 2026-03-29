package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class ReadCapturedVarNode(private val captureIndex: Int, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any {
        @Suppress("UNCHECKED_CAST")
        val captures = frame.arguments[0] as Array<Any?>
        return captures[captureIndex]!!
    }
}
