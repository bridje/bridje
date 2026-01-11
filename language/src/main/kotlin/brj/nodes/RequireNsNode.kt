package brj.nodes

import brj.NsEnv
import brj.runtime.BridjeContext
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.Source

class RequireNsNode(
    val alias: String,
    private val fqNs: String
) : Node() {

    companion object {
        private fun nsNameToResourcePath(nsName: String): String =
            nsName.replace(':', '/') + ".brj"
    }

    @TruffleBoundary
    private fun resolveSlowPath(ctx: BridjeContext): NsEnv {
        // Quarantined - re-evaluate
        ctx.globalEnv.quarantined[fqNs]?.let { source ->
            ctx.truffleEnv.parsePublic(source).call()
            return ctx.namespaces[fqNs]
                ?: error("Namespace $fqNs not registered after re-evaluation")
        }

        // Try classpath loading
        return loadFromClasspath(ctx)
    }

    private fun loadFromClasspath(ctx: BridjeContext): NsEnv {
        if (fqNs in ctx.loadingInProgress) {
            error("Circular dependency detected: $fqNs")
        }

        ctx.loadingInProgress.add(fqNs)

        try {
            val resourcePath = nsNameToResourcePath(fqNs)
            val resourceUrl = RequireNsNode::class.java.classLoader.getResource(resourcePath)
                ?: error("Namespace not found on classpath: $fqNs (looked for $resourcePath)")

            val source = Source.newBuilder("bridje", resourceUrl).build()
            ctx.truffleEnv.parsePublic(source).call()

            return ctx.namespaces[fqNs]
                ?: error("Namespace $fqNs not registered after loading from $resourcePath")
        } finally {
            ctx.loadingInProgress.remove(fqNs)
        }
    }

    fun execute(frame: VirtualFrame): NsEnv {
        val ctx = BridjeContext.get(this)
        return ctx.namespaces[fqNs] ?: resolveSlowPath(ctx)
    }
}
