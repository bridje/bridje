package brj

import brj.Analyser.Companion
import brj.Analyser.Companion.analyseValueExpr
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

    private val emitter = Emitter(frameDescriptor)

    @TruffleBoundary
    fun evalForms(): ExprNode {
        return emitter.emitExpr(analyseValueExpr(forms.first()))
    }

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        CompilerDirectives.transferToInterpreter()
        return evalForms().execute(frame)
    }
}