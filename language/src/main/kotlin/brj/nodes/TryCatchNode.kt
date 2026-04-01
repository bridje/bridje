package brj.nodes

import brj.BridjeNode
import brj.runtime.Anomaly
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.source.SourceSection

class TryCatchNode(
    @field:Child private var bodyNode: BridjeNode,
    @field:Children private val catchBranchNodes: Array<CaseBranchNode>,
    @field:Child private var finallyNode: BridjeNode?,
    loc: SourceSection? = null
) : BridjeNode(loc) {

    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any? {
        val result: Any?
        try {
            result = bodyNode.execute(frame)
        } catch (ex: Anomaly) {
            try {
                return matchCatch(frame, ex)
            } finally {
                finallyNode?.execute(frame)
            }
        }
        finallyNode?.execute(frame)
        return result
    }

    private fun matchCatch(frame: VirtualFrame, ex: Anomaly): Any? {
        for (branch in catchBranchNodes) {
            val branchResult = branch.tryMatch(frame, ex, interop)
            if (branchResult != null || branch is DefaultBranchNode) {
                return branchResult
            }
        }

        throw ex
    }
}
