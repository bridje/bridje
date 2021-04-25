package brj

import brj.nodes.EvalRootNodeGen
import brj.nodes.ExprNode
import brj.nodes.ValueExprRootNode
import brj.nodes.builtins.*
import brj.runtime.BridjeContext
import brj.runtime.BridjeFunction
import brj.runtime.BridjeView
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

    private fun addBuiltIn(ctx: BridjeContext, sym: Symbol, typing: Typing, node: ExprNode) {
        ctx.def(
            sym, typing, BridjeFunction(
                Truffle.getRuntime().createCallTarget(
                    ValueExprRootNode.create(this, FrameDescriptor(), node)
                )
            )
        )
    }

    override fun initializeContext(ctx: BridjeContext) {
        addBuiltIn(
            ctx,
            symbol("println0"),
            Typing(FnType(listOf(StringType), StringType)),
            PrintlnNodeGen.create(this, PrintWriter(ctx.truffleEnv.out()))
        )

        addBuiltIn(
            ctx, symbol("now-ms0"),
            Typing(FnType(emptyList(), IntType)),
            NowNode(this)
        )

        addBuiltIn(ctx, symbol("zero?"), Typing(FnType(listOf(IntType), BoolType)), ZeroNode(this))
        addBuiltIn(ctx, symbol("dec"), Typing(FnType(listOf(IntType), IntType)), DecNode(this))

        TypeVar().let { tv ->
            addBuiltIn(
                ctx,
                symbol("conjv0"),
                Typing(FnType(listOf(VectorType(tv), tv), VectorType(tv))),
                ConjNode(this)
            )
        }

        addBuiltIn(ctx, symbol("poly"), Typing(FnType(listOf(StringType), TypeVar())), PolyNodeGen.create(this))
        addBuiltIn(ctx, symbol("pr-str"), Typing(FnType(listOf(TypeVar()), StringType)), PrStrNodeGen.create(this))
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = FormReader(request.source).use { it.readForms().toList() }
        return Truffle.getRuntime().createCallTarget(EvalRootNodeGen.create(this, forms))
    }

    override fun getScope(ctx: BridjeContext): TruffleObject = ctx

    public override fun getLanguageView(context: BridjeContext?, value: Any) = BridjeView(value)
}
