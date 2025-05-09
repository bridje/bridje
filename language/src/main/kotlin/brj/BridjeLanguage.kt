package brj

import brj.BridjeLanguage.BridjeContext
import brj.Reader.Companion.readForms
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

@Registration(
    id = "brj",
    name = "bridje",
    defaultMimeType = "text/brj",
    characterMimeTypes = ["text/brj"],
    contextPolicy = ContextPolicy.EXCLUSIVE
)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    class BridjeContext

    override fun createContext(env: Env) = BridjeContext()

    class EvalNode(lang: BridjeLanguage, node: BridjeNode) : RootNode(lang) {
        @Child
        private var node = insert(node)

        override fun execute(frame: VirtualFrame) = node.execute(frame)
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = request.source.readForms()

        return object : RootNode(this) {

            @TruffleBoundary
            private fun eval(form: Form): Any? {
                val expr = analyseForm(form)
                val node = emitExpr(expr)

                return EvalNode(this@BridjeLanguage, node).callTarget.call()
            }

            private fun eval(forms: Sequence<Form>): Any? {
                var res: Any? = null // TODO Bridje nil

                for (form in forms) {
                    res = eval(form)
                }

                return res
            }

            override fun execute(frame: VirtualFrame) = eval(forms)

        }.callTarget
    }
}