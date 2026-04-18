package brj.builtins

import brj.BridjeLanguage
import brj.QSymbolForm
import brj.runtime.Anomaly.Companion.incorrect
import brj.runtime.BridjeContext
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class VarMetaNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doVarMeta(frame.arguments[0])

    @TruffleBoundary
    private fun doVarMeta(arg: Any?): Any {
        val qsym = arg as? QSymbolForm
            ?: throw incorrect("varMeta: expected a qualified symbol, got ${arg?.let { it::class.simpleName }}", this)

        val ctx = BridjeContext.get(this)
        val nsEnv = ctx.namespaces[qsym.namespace]
            ?: throw incorrect("varMeta: namespace not found: ${qsym.namespace}", this)
        val gv = nsEnv[qsym.member] ?: nsEnv.effectVar(qsym.member)
            ?: throw incorrect("varMeta: var not found: ${qsym.namespace}/${qsym.member}", this)

        return gv.meta
    }
}
