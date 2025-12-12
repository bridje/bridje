package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.nodes.Node.Children

class InvokeNode(
    @field:Child private var fnNode: BridjeNode,
    @field:Children private val argNodes: Array<BridjeNode>
) : BridjeNode() {

    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        val fn = fnNode.execute(frame)

        val args = arrayOfNulls<Any>(argNodes.size)
        for (i in argNodes.indices) {
            args[i] = argNodes[i].execute(frame)
        }

        return try {
            interop.execute(fn, *args)
        } catch (e: UnsupportedMessageException) {
            throw RuntimeException("Not callable: $fn")
        } catch (e: ArityException) {
            throw RuntimeException("Wrong arity: expected ${e.expectedMinArity}, got ${e.actualArity}")
        } catch (e: UnsupportedTypeException) {
            throw RuntimeException("Unsupported argument type")
        }
    }
}
