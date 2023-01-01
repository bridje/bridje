package brj.nodes

import brj.*
import brj.runtime.FxMap
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.TruffleLanguage.LanguageReference
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.RootNode

private val LANG_REF = LanguageReference.create(BridjeLanguage::class.java)
private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

internal abstract class EvalRootNode(lang: BridjeLanguage, private val forms: List<Form>) : RootNode(lang) {
//    private val typeLogger = TruffleLogger.getLogger("brj", "type")

    @Specialization
    @TruffleBoundary
    fun execute(): Any? {
        CompilerDirectives.transferToInterpreter()

        val lang = LANG_REF[this]
        val ctx = CTX_REF[this]

        val (hasNsForm, nsCtx, otherForms) =
            forms.firstOrNull()?.zip
                ?.takeIf { it.isNsForm() }
                ?.let { Triple(true, it.analyseNs(ctx), forms.drop(1)) }
                ?: Triple(false, ctx.currentNsContext, forms)

        val exprAnalyser = ExprAnalyser(ctx, nsCtx)

        val nses = ctx.nses.toMutableMap()

        nses[nsCtx.ns] = nsCtx

        fun ValueExpr.eval(): Any {
            // TODO
            // typeLogger.info("type: ${valueExprTyping(expr)}")

            val frameDescriptor = FrameDescriptor()

            val rootNode = ValueExprRootNodeGen.create(
                lang, frameDescriptor,
                WriteLocalNodeGen.create(
                    lang, ReadArgNode(lang, 0),
                    frameDescriptor.findOrAddAuxiliarySlot(DEFAULT_FX_LOCAL)
                ),
                ValueExprEmitter(lang, frameDescriptor).emitValueExpr(this)
            )

            val callNode = DirectCallNode.create(rootNode.callTarget)

            return insert(callNode).call(FxMap(FxMap.DEFAULT_SHAPE))
        }

        fun Form.evalForm(): Any? =
            when (val doOrExpr = exprAnalyser.analyseExpr(this)) {
                is TopLevelDo -> doOrExpr.forms.fold(null) { _: Any?, form -> form.evalForm() }

                is TopLevelExpr -> {
                    when (val expr = doOrExpr.expr) {
                        is ValueExpr -> expr.eval()

                        is DefExpr -> {
                            val exprVal = expr.expr.eval()
                            nsCtx.def(expr.sym, Typing(TypeVar()), exprVal)
                            exprVal
                        }

                        is DefxExpr -> nsCtx.defx(expr.sym, expr.typing)
                    }
                }
            }

        val res = otherForms.fold(null) { _: Any?, form -> form.evalForm() }

        ctx.nses = nses

        return if (hasNsForm) nsCtx else res
    }
}
