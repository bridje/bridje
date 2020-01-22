package brj

import brj.analyser.EvalAnalyser.analyseForms
import brj.emitter.BridjeObject
import brj.emitter.EvalEmitter.emitEvalExprs
import brj.emitter.ValueNode
import brj.emitter.formsNSEnv
import brj.reader.FormReader.Companion.readSourceForms
import brj.runtime.SymKind.ID
import brj.runtime.Symbol
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Scope
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.0",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    internal inner class BridjeRootNode(@Child var node: ValueNode, frameDescriptor: FrameDescriptor = FrameDescriptor()) : RootNode(this, frameDescriptor) {
        override fun execute(frame: VirtualFrame) = node.execute(frame)
    }

    override fun createContext(truffleEnv: Env) = BridjeContext(this, truffleEnv)

    override fun initializeContext(ctx: BridjeContext) {
        ctx.env += formsNSEnv(ctx)
        ctx.require(Symbol(ID, "brj.core"))
    }

    override fun isObjectOfLanguage(obj: Any) = obj is BridjeObject

    override fun parse(request: ParsingRequest): CallTarget =
        Truffle.getRuntime().createCallTarget(BridjeRootNode(emitEvalExprs(analyseForms(readSourceForms(request.source)))))

    override fun findTopScopes(ctx: BridjeContext) =
        listOf(Scope.newBuilder("global", ctx.env).build())

    companion object {
        @Deprecated("only used in tests")
        internal fun currentBridjeContext() = getCurrentContext(BridjeLanguage::class.java)
    }
}
