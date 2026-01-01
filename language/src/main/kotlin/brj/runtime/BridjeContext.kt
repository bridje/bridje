package brj.runtime

import brj.BridjeLanguage
import brj.NsEnv
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.TruffleLanguage.Env
import com.oracle.truffle.api.nodes.Node

class BridjeContext(val truffleEnv: Env, val lang: BridjeLanguage) {
    val brjCore: NsEnv = NsEnv.withBuiltins(lang)
    var namespaces: Map<String, NsEnv> = mapOf("brj:core" to brjCore)

    companion object {
        private val CONTEXT_REF: ContextReference<BridjeContext> =
            ContextReference.create(BridjeLanguage::class.java)

        @JvmStatic
        fun get(node: Node): BridjeContext = CONTEXT_REF.get(node)
    }
}
