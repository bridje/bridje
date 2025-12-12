package brj

import brj.BridjeLanguage.BridjeContext
import brj.Reader.Companion.readForms
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

@Registration(
    id = "bridje",
    name = "bridje",
    defaultMimeType = "text/brj",
    characterMimeTypes = ["text/brj"],
    contextPolicy = ContextPolicy.EXCLUSIVE
)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    class BridjeContext

    override fun createContext(env: Env) = BridjeContext()

    class EvalNode(
        lang: BridjeLanguage,
        frameDescriptor: FrameDescriptor,
        node: BridjeNode
    ) : RootNode(lang, frameDescriptor) {
        @Child
        private var node = insert(node)

        override fun execute(frame: VirtualFrame) = node.execute(frame)
    }

    private fun buildFrameDescriptor(slotCount: Int): FrameDescriptor {
        val builder = FrameDescriptor.newBuilder()
        repeat(slotCount) {
            builder.addSlot(FrameSlotKind.Illegal, null, null)
        }
        return builder.build()
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = request.source.readForms()

        return object : RootNode(this) {

            @TruffleBoundary
            private fun eval(form: Form): Any? {
                val analyser = Analyser()
                val expr = analyser.analyseForm(form)
                val frameDescriptor = buildFrameDescriptor(analyser.slotCount)
                val emitter = Emitter(this@BridjeLanguage)
                val node = emitter.emitExpr(expr)

                return EvalNode(this@BridjeLanguage, frameDescriptor, node).callTarget.call()
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