package brj

import brj.nodes.ExprNode
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

internal abstract class EvalRootNode(
    lang: BridjeLanguage,
    private val forms: List<Form>,
) : RootNode(lang) {

    private val emitter = Emitter(lang, frameDescriptor)

    @TruffleBoundary
    fun evalForms(ctx: BridjeContext): ExprNode =
        when (val expr = Analyser(ctx.bridjeEnv).analyseExpr(forms.first())) {
            is ValueExpr -> emitter.emitValueExpr(expr)
            is DefExpr -> emitter.emitDefExpr(expr)
        }

    @ExplodeLoop
    @Specialization
    fun execute(
        frame: VirtualFrame,
        @CachedContext(BridjeLanguage::class) ctx: BridjeContext
    ): Any {
        CompilerDirectives.transferToInterpreter()
        return insert(evalForms(ctx)).execute(frame)
    }
}