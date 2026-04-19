package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class LangNode(
    private val value: Any?,
    loc: SourceSection?,
) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any? = value
}
