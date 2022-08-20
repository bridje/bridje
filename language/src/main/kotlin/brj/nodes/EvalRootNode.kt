package brj.nodes

import brj.*
import brj.runtime.FxMap
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.TruffleLanguage.LanguageReference
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.nodes.RootNode

private val LANG_REF = LanguageReference.create(BridjeLanguage::class.java)
private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

internal abstract class EvalRootNode(lang: BridjeLanguage, private val forms: List<Form>) : RootNode(lang) {
//    private val typeLogger = TruffleLogger.getLogger("brj", "type")

    @Specialization
    @TruffleBoundary
    fun execute(): Any? {
        CompilerDirectives.transferToInterpreter()

        var res: Any? = null
        val lang = LANG_REF[this]
        val ctx = CTX_REF[this]

        for (form in forms) {
            val rootNode = when (val doOrExpr = ExprAnalyser(ctx).analyseExpr(form)) {
                is TopLevelDo -> EvalRootNodeGen.create(lang, doOrExpr.forms)
                is TopLevelExpr -> when (val expr = doOrExpr.expr) {
                    is ValueExpr -> {
                        // TODO
//                        typeLogger.info("type: ${valueExprTyping(expr)}")

                        val frameDescriptor = FrameDescriptor()
                        ValueExprRootNodeGen.create(
                            lang, frameDescriptor,
                            WriteLocalNodeGen.create(
                                lang, ReadArgNode(lang, 0),
                                frameDescriptor.findOrAddFrameSlot(DEFAULT_FX_LOCAL)
                            ),
                            ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr)
                        )
                    }

                    is DefExpr -> {
                        // TODO
//                        val valueExprTyping = valueExprTyping(expr.expr)
//                        typeLogger.info("type: $valueExprTyping")

                        val frameDescriptor = FrameDescriptor()
                        DefRootNodeGen.create(
                            lang, frameDescriptor,
                            expr.sym, Typing(TypeVar()), /*valueExprTyping*/ expr.loc,
                            ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr.expr),
                        )
                    }

                    is DefxExpr -> DefxRootNodeGen.create(lang, expr.sym, expr.typing, expr.loc)

                    is ImportExpr -> ImportRootNodeGen.create(lang, expr.loc, expr.syms.toTypedArray())
                }
            }

            val callNode = Truffle.getRuntime().createDirectCallNode(rootNode.callTarget)

            res = insert(callNode).call(FxMap(FxMap.DEFAULT_SHAPE))
        }

        return res
    }
}
