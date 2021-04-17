package brj

import brj.nodes.*
import brj.nodes.builtins.*
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

    private fun BridjeContext.addBuiltIn(sym: Symbol, typing: Typing, node: ExprNode) {
        bridjeEnv.def(
            sym, typing, BridjeFunction(
                Truffle.getRuntime().createCallTarget(
                    ValueExprRootNode.create(this@BridjeLanguage, FrameDescriptor(), node)
                )
            )
        )
    }

    override fun initializeContext(ctx: BridjeContext) {
        ctx.addBuiltIn(
            symbol("println0"),
            Typing(FnType(listOf(StringType), StringType)),
            PrintlnNodeGen.create(this, PrintWriter(ctx.truffleEnv.out()))
        )

        ctx.addBuiltIn(symbol("now-ms0"), Typing(FnType(emptyList(), IntType)),
            NowNode(this)
        )

        ctx.addBuiltIn(symbol("zero?"), Typing(FnType(listOf(IntType), BoolType)), ZeroNode(this))
        ctx.addBuiltIn(symbol("dec"), Typing(FnType(listOf(IntType), IntType)), DecNode(this))

        TypeVar().let { tv ->
            ctx.addBuiltIn(symbol("conjv0"), Typing(FnType(listOf(VectorType(tv), tv), VectorType(tv))), ConjNode(this))
        }

        ctx.addBuiltIn(symbol("jclass"), Typing(FnType(listOf(StringType), TypeVar())), JClassNodeGen.create(this))
        ctx.addBuiltIn(symbol("poly"), Typing(FnType(listOf(StringType), TypeVar())), PolyNodeGen.create(this))
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = FormReader(request.source).use { it.readForms().toList() }
        return Truffle.getRuntime().createCallTarget(EvalRootNodeGen.create(this, forms))
    }

    override fun getScope(context: BridjeContext): TruffleObject = context.bridjeEnv
}
