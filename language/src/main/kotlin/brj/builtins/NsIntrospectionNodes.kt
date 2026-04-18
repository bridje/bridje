package brj.builtins

import brj.BridjeLanguage
import brj.runtime.Anomaly.Companion.incorrect
import brj.runtime.BridjeContext
import brj.runtime.BridjeVector
import brj.runtime.Symbol
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class AllNsesNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doAllNses()

    @TruffleBoundary
    private fun doAllNses(): BridjeVector =
        BridjeVector(BridjeContext.get(this).namespaces.keys.map { Symbol.intern(it) })
}

class NsVarsNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doNsVars(frame.arguments[0])

    @TruffleBoundary
    private fun doNsVars(arg: Any?): BridjeVector {
        val ns = arg as? Symbol
            ?: throw incorrect("nsVars: expected a Symbol, got ${arg?.let { it::class.simpleName }}", this)

        val nsEnv = BridjeContext.get(this).namespaces[ns.name]
            ?: throw incorrect("nsVars: namespace not found: ${ns.name}", this)

        return BridjeVector(nsEnv.vars.values.toList())
    }
}
