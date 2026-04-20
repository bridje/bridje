package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.source.SourceSection

class RecordUpdateNode(
    private val keys: Array<String>,
    @field:Child private var recordNode: BridjeNode,
    @field:Children private val valueNodes: Array<BridjeNode>,
    loc: SourceSection? = null
) : BridjeNode(loc) {

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        var record = recordNode.execute(frame) as BridjeRecord
        for (i in keys.indices) {
            val value = valueNodes[i].execute(frame)
            record = record.put(keys[i], value)
        }
        return record
    }
}
