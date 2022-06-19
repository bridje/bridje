package brj

import brj.builtins.*
import brj.lsp.startLspServer
import brj.nodes.EvalRootNodeGen
import brj.nodes.ExprNode
import brj.nodes.ReadArgNode
import brj.nodes.ValueExprRootNode
import brj.runtime.BridjeContext
import brj.runtime.BridjeFunction
import brj.runtime.BridjeView
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.NodeFactory
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.interop.TruffleObject
import org.graalvm.options.*

private val LSP_OPTION_KEY: OptionKey<Boolean> = OptionKey(false)

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.3",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED
)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    override fun getOptionDescriptors(): OptionDescriptors =
        OptionDescriptors.create(
            listOf(
                OptionDescriptor.newBuilder(LSP_OPTION_KEY, "brj.lsp")
                    .stability(OptionStability.STABLE)
                    .category(OptionCategory.USER)
                    .help("starts an LSP server, communicating on stdin/stdout")
                    .build()
            )
        )

    override fun createContext(truffleEnv: Env) = BridjeContext(this, truffleEnv)

    private fun builtInFunction(node: ExprNode) = BridjeFunction(
        ValueExprRootNode.create(this, FrameDescriptor(), node).callTarget
    )

    private fun installBuiltIns(ctx: BridjeContext) {
        fun installBuiltIn(
            factory: NodeFactory<out BuiltInFn>,
            typing: Typing = Typing(TypeVar()),
            passFx: Boolean = false
        ) {
            val ann = factory.nodeClass.getAnnotation(BuiltIn::class.java)
            val argNodes = Array(factory.executionSignature.size) { ReadArgNode(this, it + (if (passFx) 0 else 1)) }

            ctx.def(ann.name.sym, typing, builtInFunction(factory.createNode(this, argNodes)))
        }

        installBuiltIn(ReduceFnFactory.getInstance(), passFx = true)

        installBuiltIn(PlusFnFactory.getInstance())
        installBuiltIn(MinusFnFactory.getInstance())
        installBuiltIn(MultiplyFnFactory.getInstance())
        installBuiltIn(DivideFnFactory.getInstance())
        installBuiltIn(IncFnFactory.getInstance())
        installBuiltIn(DecFnFactory.getInstance())

        installBuiltIn(EqualsFnFactory.getInstance())
        installBuiltIn(GreaterThanEqualsFnFactory.getInstance())
        installBuiltIn(GreaterThanFnFactory.getInstance())
        installBuiltIn(LessThanEqualsFnFactory.getInstance())
        installBuiltIn(LessThanFnFactory.getInstance())
        installBuiltIn(IsZeroFnFactory.getInstance())

        ctx.defx("now!".sym, Typing(TypeVar()))
        installBuiltIn(NowNodeFactory.getInstance())
        installBuiltIn(PolyNodeFactory.getInstance())
        installBuiltIn(PrStrNodeFactory.getInstance())

        ctx.defx("println!".sym, Typing(TypeVar()))
        installBuiltIn(PrintlnNodeFactory.getInstance())
    }

    private var lspThread: Thread? = null

    override fun initializeContext(ctx: BridjeContext) {
        installBuiltIns(ctx)

        if (ctx.truffleEnv.options[LSP_OPTION_KEY]) {
            lspThread = ctx.truffleEnv.createThread { startLspServer(ctx) }
                .also { t -> t.name = "brj-lsp"; t.start() }
        }
    }

    override fun finalizeContext(context: BridjeContext) {
        lspThread?.join()
    }

    override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true

    override fun parse(request: ParsingRequest): CallTarget =
        EvalRootNodeGen.create(this, readForms(request.source)).callTarget

    override fun getScope(ctx: BridjeContext): TruffleObject = ctx

    public override fun getLanguageView(context: BridjeContext, value: Any) = BridjeView(value)
}
