package brj.nodes

import brj.*
import brj.runtime.FxMap
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLogger
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.CachedLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.nodes.RootNode

internal abstract class EvalRootNode(lang: BridjeLanguage, private val forms: List<Form>) : RootNode(lang) {
    private val typeLogger = TruffleLogger.getLogger("brj", "type")

    @Specialization
    @TruffleBoundary
    fun execute(@CachedLanguage lang: BridjeLanguage, @CachedContext(BridjeLanguage::class) ctx: BridjeContext): Any? {
        CompilerDirectives.transferToInterpreter()

        var res: Any? = null

        for (form in forms) {
            val rootNode = when (val doOrExpr = Analyser(ctx.bridjeEnv).analyseExpr(form)) {
                is TopLevelDo -> EvalRootNodeGen.create(lang, doOrExpr.forms)
                is TopLevelExpr -> when (val expr = doOrExpr.expr) {
                    is ValueExpr -> {
//                        typeLogger.info("type: ${valueExprTyping(expr)}")

                        val frameDescriptor = FrameDescriptor()
                        ValueExprRootNodeGen.create(
                            lang, frameDescriptor,
                            ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr)
                        )
                    }

                    is DefExpr -> {
                        val valueExprType = valueExprTyping(expr.expr)
                        typeLogger.info("type: $valueExprType")

                        val frameDescriptor = FrameDescriptor()
                        DefRootNodeGen.create(
                            lang, frameDescriptor,
                            ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr.expr),
                            expr.sym, valueExprType, expr.loc
                        )
                    }

                    is DefxExpr -> DefxRootNodeGen.create(lang, expr.sym, expr.typing, expr.loc)

                    is ImportExpr -> ImportRootNodeGen.create(lang, expr.syms.toTypedArray(), expr.loc)
                }
            }

            val callNode = Truffle.getRuntime().createDirectCallNode(Truffle.getRuntime().createCallTarget(rootNode))

            res = insert(callNode).call(FxMap(FxMap.DEFAULT_SHAPE))
        }

        return res
    }
}
