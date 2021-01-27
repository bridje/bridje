package brj.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

internal class BoolNode(private val bool: Boolean, override val loc: SourceSection?) : ExprNode() {
    override fun execute(frame: VirtualFrame) = bool
}