package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeNull
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.source.SourceSection

class RecordSetNode(
    private val key: String,
    @field:Child private var recordNode: BridjeNode,
    @field:Child private var valueNode: BridjeNode,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any {
        val record = recordNode.execute(frame) as BridjeRecord
        val value = valueNode.execute(frame)
        return record.set(key, value) ?: BridjeNull
    }
}
