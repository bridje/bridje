package brj

import brj.BridjeLanguage.BridjeContext
import brj.Reader.Companion.readForms
import brj.analyser.*
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
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode

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

    class BridjeContext(val truffleEnv: Env, val lang: BridjeLanguage) {
        val brjCore: NsEnv = NsEnv.withBuiltins(lang)
        var namespaces: Map<String, NsEnv> = mapOf("brj:core" to brjCore)
    }

    override fun createContext(env: Env) = BridjeContext(env, this)

    override fun getScope(context: BridjeContext): Any = BridjeScope(context.namespaces)

    @ExportLibrary(InteropLibrary::class)
    class BridjeScope(private val namespaces: Map<String, NsEnv>) : TruffleObject {
        @ExportMessage
        fun hasLanguage() = true

        @ExportMessage
        fun getLanguage(): Class<BridjeLanguage> = BridjeLanguage::class.java

        @ExportMessage
        fun isScope() = true

        @ExportMessage
        fun hasMembers() = true

        @ExportMessage
        @TruffleBoundary
        fun getMembers(includeInternal: Boolean) = 
            BridjeRecord.Keys(namespaces.keys.toTypedArray())

        @ExportMessage
        @TruffleBoundary
        fun isMemberReadable(member: String) = member in namespaces

        @ExportMessage
        @TruffleBoundary
        @Throws(UnknownIdentifierException::class)
        fun readMember(member: String) = 
            namespaces[member] ?: throw UnknownIdentifierException.create(member)

        @ExportMessage
        @TruffleBoundary
        fun toDisplayString(allowSideEffects: Boolean) = "bridje"
    }

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
        val (nsDecl, forms) = request.source.readForms().toList().analyseNs()

        return object : RootNode(this) {
            private fun resolveRequires(ctx: BridjeContext): Requires =
                nsDecl
                    ?.requires.orEmpty()
                    .mapValues { (alias, fqNs) ->
                        ctx.namespaces[fqNs] ?: error("Required namespace not found: $fqNs (as $alias)")
                    }

            private fun NsDecl.resolve(ctx: BridjeContext): NsEnv =
                NsEnv(
                    requires = resolveRequires(ctx),
                    imports = this.imports
                )

            @TruffleBoundary
            private fun evalExpr(expr: ValueExpr, slotCount: Int): Any? {
                val frameDescriptor = buildFrameDescriptor(slotCount)
                val emitter = Emitter(this@BridjeLanguage)
                val node = emitter.emitExpr(expr)
                return EvalNode(this@BridjeLanguage, frameDescriptor, node).callTarget.call()
            }

            @TruffleBoundary
            private fun List<Form>.evalForms(ctx: BridjeContext, nsEnv: NsEnv): Pair<NsEnv, Any?> {
                var nsEnv = nsEnv

                val res = fold(null as Any?) { _, form ->

                    val analyser = Analyser(ctx, nsEnv)

                    when (val result = analyser.analyseTopLevel(form)) {
                        is TopLevelDo -> {
                            val (newNsEnv, res) = result.forms.evalForms(ctx, nsEnv)
                            nsEnv = newNsEnv
                            res
                        }
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

                return Pair(nsEnv, res)
            }

            @TruffleBoundary
            override fun execute(frame: VirtualFrame): Any? {
                val ctx = CONTEXT_REF.get(this)

                val (nsEnv, result) = forms.evalForms(ctx, nsDecl?.resolve(ctx) ?: NsEnv())

                if (nsDecl != null) {
                    ctx.namespaces = ctx.namespaces + (nsDecl.name to nsEnv)
                }

                return result
            }
        }.callTarget
    }
}
