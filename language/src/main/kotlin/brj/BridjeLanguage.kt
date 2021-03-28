package brj

import brj.nodes.*
import brj.runtime.BridjeFunction
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.interop.TruffleObject
import java.io.PrintWriter

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.2",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED
)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    override fun createContext(truffleEnv: Env) = BridjeContext(truffleEnv)

    private fun BridjeContext.addBuiltIn(sym: Symbol, type: MonoType, node: ExprNode) {
        bridjeEnv.def(
            sym, type, BridjeFunction(
                Truffle.getRuntime().createCallTarget(
                    ValueExprRootNodeGen.create(this@BridjeLanguage, FrameDescriptor(), node)
                )
            )
        )
    }

    override fun initializeContext(ctx: BridjeContext) {
        ctx.addBuiltIn(
            symbol("println0"),
            FnType(listOf(StringType), StringType),
            PrintlnNodeGen.create(this, PrintWriter(ctx.truffleEnv.out()))
        )
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = FormReader(request.source).use { it.readForms().toList() }
        return Truffle.getRuntime().createCallTarget(EvalRootNodeGen.create(this, forms))
    }

    override fun getScope(context: BridjeContext): TruffleObject = context.bridjeEnv
}
