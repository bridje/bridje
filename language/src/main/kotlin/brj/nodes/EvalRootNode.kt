package brj.nodes

import brj.*
import brj.nodes.EvalRootNodeGen.*
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLogger
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection

internal abstract class EvalRootNode(lang: BridjeLanguage, private val forms: List<Form>) : RootNode(lang) {
    abstract class EvalFormNode(lang: BridjeLanguage, private val form: Form) : RootNode(lang) {

        private val emitter = Emitter(lang, frameDescriptor)

        override fun getSourceSection(): SourceSection? {
            return form.loc
        }

        @Specialization
        fun doExecute(frame: VirtualFrame, @CachedContext(BridjeLanguage::class) ctx: BridjeContext): Any {
            CompilerDirectives.transferToInterpreter()

            val exprNode = when (val expr = Analyser(ctx.bridjeEnv).analyseExpr(form)) {
                is ValueExpr -> {
                    TruffleLogger.getLogger("brj", "type").info("type: ${valueExprType(expr)}")
                    emitter.emitValueExpr(expr)
                }
                is DefExpr -> {
                    TruffleLogger.getLogger("brj", "type").info("type: ${valueExprType(expr.expr)}")
                    emitter.emitDefExpr(expr)
                }
            }

            return insert(exprNode).execute(frame)
        }
    }

    class EvalFormCallNode(
        lang: BridjeLanguage,
        private val callTarget: CallTarget,
        private val sourceSection: SourceSection?
    ) : ExprNode(lang) {
        override fun getSourceSection() = sourceSection
        override fun execute(frame: VirtualFrame?): Any = callTarget.call()
    }

    @TruffleBoundary
    private fun evalForms(lang: BridjeLanguage) =
        ExecuteArrayNode(lang, forms.map { form ->
            EvalFormCallNode(lang, Truffle.getRuntime().createCallTarget(EvalFormNodeGen.create(lang, form)), form.loc)
        }.toTypedArray())

    @Specialization
    fun execute(frame: VirtualFrame, @CachedLanguage lang: BridjeLanguage): Any {
        CompilerDirectives.transferToInterpreter()
        val res = insert(evalForms(lang)).execute(frame)
        return res[res.size - 1]
    }
}
