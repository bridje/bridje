package brj.runtime

import brj.BridjeLanguage
import brj.NsEnv
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.TruffleLanguage.Env
import com.oracle.truffle.api.nodes.Node

class BridjeContext(val truffleEnv: Env, val lang: BridjeLanguage) {
    val brjCore: NsEnv = NsEnv.withBuiltins(lang)

    var globalEnv: GlobalEnv = GlobalEnv(namespaces = mapOf("brj:core" to brjCore))
        private set

    // Track namespaces currently being loaded to detect circular dependencies
    val loadingInProgress: MutableSet<String> = mutableSetOf()

    // Convenience accessor for backwards compatibility
    val namespaces: Map<String, NsEnv>
        get() = globalEnv.namespaces

    fun updateGlobalEnv(update: (GlobalEnv) -> GlobalEnv) {
        globalEnv = update(globalEnv)
    }

    companion object {
        private val CONTEXT_REF: ContextReference<BridjeContext> =
            ContextReference.create(BridjeLanguage::class.java)

        @JvmStatic
        fun get(node: Node): BridjeContext = CONTEXT_REF.get(node)
    }
}
