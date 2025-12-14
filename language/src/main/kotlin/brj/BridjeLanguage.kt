package brj

import brj.BridjeLanguage.BridjeContext
import brj.Reader.Companion.readForms
import brj.runtime.BridjeTagConstructor
import brj.runtime.BridjeTaggedSingleton
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

    companion object {
        private val CONTEXT_REF: ContextReference<BridjeContext> = ContextReference.create(BridjeLanguage::class.java)
    }

    class BridjeContext(val env: Env)

    override fun createContext(env: Env) = BridjeContext(env)

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
            private var globalEnv = GlobalEnv()

            @TruffleBoundary
            private fun evalExpr(expr: ValueExpr, slotCount: Int): Any? {
                val frameDescriptor = buildFrameDescriptor(slotCount)
                val emitter = Emitter(this@BridjeLanguage)
                val node = emitter.emitExpr(expr)
                return EvalNode(this@BridjeLanguage, frameDescriptor, node).callTarget.call()
            }

            @TruffleBoundary
            private fun evalForm(form: Form, truffleEnv: Env): Any? {
                val analyser = Analyser(truffleEnv, globalEnv = globalEnv)
                return when (val result = analyser.analyseTopLevel(form)) {
                    is TopLevelDo -> result.forms.fold(null as Any?) { _, f -> evalForm(f, truffleEnv) }
                    is TopLevelExpr -> when (val expr = result.expr) {
                        is DefExpr -> {
                            val value = evalExpr(expr.valueExpr, analyser.slotCount)
                            globalEnv = globalEnv.def(expr.name, value)
                            value
                        }

                        is DefTagExpr -> {
                            val value: Any = 
                                if (expr.fieldNames.isEmpty()) {
                                    BridjeTaggedSingleton(expr.name)
                                } else {
                                    BridjeTagConstructor(expr.name, expr.fieldNames.size, expr.fieldNames)
                                }

                            globalEnv = globalEnv.def(expr.name, value)

                            value
                        }

                        is ValueExpr -> evalExpr(expr, analyser.slotCount)
                    }
                }
            }

            override fun execute(frame: VirtualFrame): Any? {
                val ctx = CONTEXT_REF.get(this)
                return forms.fold(null as Any?) { _, form -> evalForm(form, ctx.env) }
            }
        }.callTarget
    }
}
