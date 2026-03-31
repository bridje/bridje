package brj.nodes

import brj.BridjeNode
import brj.GlobalVar
import brj.runtime.BridjeFxMap
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class ReadFxMapEntryNode(
    @field:Child private var fxMapNode: BridjeNode,
    private val effectVar: GlobalVar,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any? {
        val map = fxMapNode.execute(frame) as BridjeFxMap
        return map[effectVar] ?: effectVar.value
    }
}
