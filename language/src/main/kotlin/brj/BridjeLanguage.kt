package brj

import brj.builtins.DecFunction
import brj.builtins.NowFunction
import brj.builtins.ReduceFunction
import brj.builtins.ZeroFunction
import brj.nodes.EvalRootNodeGen
import brj.nodes.ExprNode
import brj.nodes.ValueExprRootNode
import brj.nodes.builtins.PolyNodeGen
import brj.nodes.builtins.PrStrNodeGen
import brj.nodes.builtins.PrintlnNodeGen
import brj.runtime.BridjeContext
import brj.runtime.BridjeFunction
import brj.runtime.BridjeView
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

    private fun builtInFunction(node: ExprNode) = BridjeFunction(
        Truffle.getRuntime().createCallTarget(
            ValueExprRootNode.create(this, FrameDescriptor(), node)
        )
    )

    override fun initializeContext(ctx: BridjeContext) {
        ctx.def(
            symbol("println0"), Typing(FnType(listOf(StringType), StringType)),
            builtInFunction(
                PrintlnNodeGen.create(this, PrintWriter(ctx.truffleEnv.out()))
            )
        )

        val a = TypeVar("a")
        val b = TypeVar("b")

        ctx.def(symbol("reduce"), Typing(FnType(listOf(FnType(listOf(b, a), b), b, VectorType(a)), b)), ReduceFunction)

        ctx.def(symbol("now-ms0"), Typing(FnType(emptyList(), IntType)), NowFunction)
        ctx.def(symbol("zero?"), Typing(FnType(listOf(IntType), BoolType)), ZeroFunction)
        ctx.def(symbol("dec"), Typing(FnType(listOf(IntType), IntType)), DecFunction)

        ctx.def(
            symbol("poly"), Typing(FnType(listOf(StringType), TypeVar())),
            builtInFunction(PolyNodeGen.create(this))
        )

        ctx.def(
            symbol("pr-str"), Typing(FnType(listOf(TypeVar()), StringType)),
            builtInFunction(PrStrNodeGen.create(this))
        )
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = FormReader(request.source).use { it.readForms().toList() }
        return Truffle.getRuntime().createCallTarget(EvalRootNodeGen.create(this, forms))
    }

    override fun getScope(ctx: BridjeContext): TruffleObject = ctx

    public override fun getLanguageView(context: BridjeContext?, value: Any) = BridjeView(value)
}
