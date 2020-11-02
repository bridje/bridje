package brj.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

internal class StringNode(private val str: String,
                          override val loc: SourceSection?
) : ExprNode() {
    override fun execute(frame: VirtualFrame) = str
}