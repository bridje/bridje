package brj.nodes

import brj.NsEnv
import brj.runtime.BridjeContext
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

class RequireNsNode(
    val alias: String,
    private val fqNs: String
) : Node() {

    @TruffleBoundary
    private fun resolveSlowPath(ctx: BridjeContext): NsEnv {
        // Quarantined - re-evaluate
        ctx.globalEnv.quarantined[fqNs]?.let { source ->
            ctx.truffleEnv.parsePublic(source).call()
            return ctx.namespaces[fqNs]
                ?: error("Namespace $fqNs not registered after re-evaluation")
        }

        error("Required namespace not found: $fqNs")
    }

    fun execute(frame: VirtualFrame): NsEnv {
        val ctx = BridjeContext.get(this)
        return ctx.namespaces[fqNs] ?: resolveSlowPath(ctx)
    }
}
