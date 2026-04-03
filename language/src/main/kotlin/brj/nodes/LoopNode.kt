package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ControlFlowException
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.LoopNode
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RepeatingNode
import com.oracle.truffle.api.source.SourceSection

class RecurException(val args: Array<Any?>) : ControlFlowException()

class LoopRepeatingNode(
    @field:CompilationFinal(dimensions = 1) private val slots: IntArray,
    private val resultSlot: Int,
    @field:Child private var bodyNode: BridjeNode
) : Node(), RepeatingNode {
    @ExplodeLoop
    override fun executeRepeating(frame: VirtualFrame): Boolean {
        return try {
            frame.setObject(resultSlot, bodyNode.execute(frame))
            false
        } catch (e: RecurException) {
            for (i in slots.indices) {
                frame.setObject(slots[i], e.args[i])
            }
            true
        }
    }
}

class LoopBridjeNode(
    @field:CompilationFinal(dimensions = 1) private val slots: IntArray,
    private val resultSlot: Int,
    @field:Children private var initNodes: Array<BridjeNode>,
    @field:Child private var loopNode: LoopNode,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        for (i in initNodes.indices) {
            frame.setObject(slots[i], initNodes[i].execute(frame))
        }
        loopNode.execute(frame)
        return frame.getObject(resultSlot)
    }
}

class RecurNode(
    @field:Children private var argNodes: Array<BridjeNode>,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        val values = Array<Any?>(argNodes.size) { argNodes[it].execute(frame) }
        throw RecurException(values)
    }
}
