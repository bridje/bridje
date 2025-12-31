package brj

import brj.BridjeLanguage.BridjeContext
import brj.Reader.Companion.readForms
import brj.runtime.BridjeKey
import brj.runtime.BridjeMacro
import brj.runtime.BridjeRecord
import brj.runtime.BridjeTagConstructor
import brj.runtime.BridjeTaggedSingleton
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.*
import com.oracle.truffle.api.dsl.Bind
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.`object`.Shape
import java.lang.invoke.MethodHandles

@Registration(
    id = "bridje",
    name = "bridje",
    defaultMimeType = "text/brj",
    characterMimeTypes = ["text/brj"],
    contextPolicy = ContextPolicy.EXCLUSIVE
)
@Bind.DefaultExpression("get(\$node)")
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    companion object {
        private val CONTEXT_REF: ContextReference<BridjeContext> = ContextReference.create(BridjeLanguage::class.java)
        private val LANGUAGE_REF: LanguageReference<BridjeLanguage> = LanguageReference.create(BridjeLanguage::class.java)

        @JvmStatic
        fun get(node: Node): BridjeLanguage = LANGUAGE_REF.get(node)
    }

    val recordShape: Shape = Shape.newBuilder()
        .layout(BridjeRecord::class.java, MethodHandles.lookup())
        .build()

    class BridjeContext(val truffleEnv: Env, val lang: BridjeLanguage) {
        val brjCore: NsEnv = NsEnv.withBuiltins(lang)
        var namespaces: Map<String, NsEnv> = mapOf("brj:core" to brjCore)
    }

    override fun createContext(env: Env) = BridjeContext(env, this)

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
            private var nsEnv = NsEnv()

            @TruffleBoundary
            private fun evalExpr(expr: ValueExpr, slotCount: Int): Any? {
                val frameDescriptor = buildFrameDescriptor(slotCount)
                val emitter = Emitter(this@BridjeLanguage)
                val node = emitter.emitExpr(expr)
                return EvalNode(this@BridjeLanguage, frameDescriptor, node).callTarget.call()
            }

            @TruffleBoundary
            private fun evalForm(form: Form, ctx: BridjeContext): Any? {
                val analyser = Analyser(ctx, nsEnv = nsEnv)
                return when (val result = analyser.analyseTopLevel(form)) {
                    is TopLevelDo -> result.forms.fold(null as Any?) { _, f -> evalForm(f, ctx) }
                    is TopLevelExpr -> when (val expr = result.expr) {
                        is DefExpr -> {
                            val value = evalExpr(expr.valueExpr, analyser.slotCount)
                            nsEnv = nsEnv.def(expr.name, value)
                            value
                        }

                        is DefTagExpr -> {
                            val value: Any =
                                if (expr.fieldNames.isEmpty()) {
                                    BridjeTaggedSingleton(expr.name)
                                } else {
                                    BridjeTagConstructor(expr.name, expr.fieldNames.size, expr.fieldNames)
                                }

                            nsEnv = nsEnv.def(expr.name, value)

                            value
                        }

                        is DefMacroExpr -> {
                            val fn = evalExpr(expr.fn, analyser.slotCount)
                            val macro = BridjeMacro(fn!!)
                            nsEnv = nsEnv.def(expr.name, macro)
                            macro
                        }

                        is DefKeyExpr -> {
                            val key = BridjeKey(expr.name)
                            nsEnv = nsEnv.defKey(expr.name, key)
                            key
                        }

                        is ValueExpr -> evalExpr(expr, analyser.slotCount)
                    }
                }
            }

            override fun execute(frame: VirtualFrame): Any? {
                val ctx = CONTEXT_REF.get(this)

                val firstForm = forms.firstOrNull()
                if (firstForm is ListForm && (firstForm.els.firstOrNull() as? SymbolForm)?.name == "ns") {
                    TODO("ns declaration")
                }

                return forms.fold(null as Any?) { _, form -> evalForm(form, ctx) }
            }
        }.callTarget
    }
}
