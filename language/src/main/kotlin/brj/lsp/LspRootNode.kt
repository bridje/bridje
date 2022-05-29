package brj.lsp

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import brj.runtime.Nil
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.RootNode

private val CTX_REF: ContextReference<BridjeContext> = ContextReference.create(BridjeLanguage::class.java)

internal abstract class LspRootNode(lang: BridjeLanguage): RootNode(lang) {

    @TruffleBoundary
    @Specialization
    fun execute(): Any {
        startLspServer(CTX_REF[this])
        return Nil
    }
}