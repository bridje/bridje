package brj

import brj.Analyser.Companion.analyseExpr
import brj.nodes.ExprNode
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

internal class EvalRootNode(
    lang: BridjeLanguage,
    private val forms: List<Form>,
) : RootNode(lang) {

    private val emitter = Emitter(lang, frameDescriptor)

    @TruffleBoundary
    fun evalForms(): ExprNode =
        when (val expr = analyseExpr(forms.first())) {
            is ValueExpr -> emitter.emitValueExpr(expr)
            is DefExpr -> emitter.emitDefExpr(expr)
        }

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        CompilerDirectives.transferToInterpreter()
        return insert(evalForms()).execute(frame)
    }
}