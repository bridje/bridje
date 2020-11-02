package brj.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

internal class IntNode(private val int: Int, override val loc: SourceSection?) : ExprNode() {
    override fun execute(frame: VirtualFrame) = int
}