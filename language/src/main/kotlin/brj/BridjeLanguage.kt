package brj

import brj.nodes.ExprNode
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.CompilerDirectives.transferToInterpreter
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.2",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    override fun createContext(truffleEnv: Env) = BridjeContext(truffleEnv)

    internal class EvalRootNode(
        private val lang: BridjeLanguage,
        private val forms: List<Form>,
    ) : RootNode(lang) {

        @TruffleBoundary
        fun evalForms(): ExprNode {
            return emitExpr(analyseValueForm(forms.first()))
        }

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            transferToInterpreter()
            return evalForms().execute(frame)
        }
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = FormReader(request.source).use { it.readForms().toList() }
        return Truffle.getRuntime().createCallTarget(EvalRootNode(this, forms))
    }
}
