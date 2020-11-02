package brj.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.SourceSection

internal abstract class ExprNode : Node() {
    abstract val loc: SourceSection?
    abstract fun execute(frame: VirtualFrame): Any

    override fun getSourceSection() = loc
}