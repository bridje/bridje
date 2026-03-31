package brj.nodes

import brj.BridjeNode
import brj.GlobalVar
import brj.runtime.BridjeFxMap
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.source.SourceSection

class BuildFxMapNode(
    @field:Child private var baseFxNode: BridjeNode,
    private val keys: Array<GlobalVar>,
    @field:Children private val valueNodes: Array<BridjeNode>,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val base = baseFxNode.execute(frame) as BridjeFxMap
        val overrides = mutableMapOf<GlobalVar, Any?>()
        for (i in keys.indices) {
            overrides[keys[i]] = valueNodes[i].execute(frame)
        }
        return base.assoc(overrides)
    }
}
