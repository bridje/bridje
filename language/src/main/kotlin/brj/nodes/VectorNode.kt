package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeVector
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame

@NodeChild(value = "els", type = ExecuteArrayNode::class)
abstract class VectorNode : BridjeNode() {

    @Specialization
    fun createVector(els: Array<Any>) = BridjeVector(els)

    abstract override fun execute(frame: VirtualFrame): Any?
}
