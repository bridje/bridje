package brj.nodes

import brj.BridjeNode
import brj.runtime.Anomaly
import brj.runtime.Anomaly.Companion.host
import brj.runtime.Anomaly.Companion.interrupted
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ExceptionType
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
        } catch (ex: AbstractTruffleException) {
            val anomaly = toAnomalyOrNull(ex)
                ?: throw ex
            try {
                return matchCatch(frame, anomaly)
            } finally {
                finallyNode?.execute(frame)
            }
        }
        finallyNode?.execute(frame)
        return result
    }

    @TruffleBoundary
    private fun toAnomalyOrNull(ex: AbstractTruffleException): Anomaly? {
        val uncached = InteropLibrary.getUncached()
        if (!uncached.isException(ex)) return null
        val message = if (uncached.hasExceptionMessage(ex))
            uncached.getExceptionMessage(ex).toString()
        else "Host exception"

        return when (uncached.getExceptionType(ex)) {
            ExceptionType.INTERRUPT -> interrupted(message, ex)
            else -> host(message, ex)
        }
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
