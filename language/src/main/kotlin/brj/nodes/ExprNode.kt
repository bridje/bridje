package brj.nodes

import brj.BridjeTypes
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.SourceSection

@TypeSystemReference(BridjeTypes::class)
internal abstract class ExprNode : Node() {
    open val loc: SourceSection? = null
    abstract fun execute(frame: VirtualFrame): Any

    override fun getSourceSection() = loc
}