package brj.nodes

import brj.*
import brj.analyser.*
import brj.runtime.*
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.nodes.Node.Children
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source

class ParseRootNode(
    private val lang: BridjeLanguage,
    private val nsDecl: NsDecl?,
    private val forms: List<Form>,
    private val source: Source,
    @field:Children private val requires: Array<RequireNsNode>
) : RootNode(lang) {

    @ExplodeLoop
    private fun resolveRequires(frame: VirtualFrame): Requires {
        val result = mutableMapOf<String, NsEnv>()
        for (node in requires) {
            result[node.alias] = node.execute(frame)
        }
        return result
    }

    private fun NsDecl.resolve(frame: VirtualFrame): NsEnv =
        NsEnv(
            requires = resolveRequires(frame),
            imports = this.imports,
            nsDecl = this,
            source = source
        )

    private fun buildFrameDescriptor(slotCount: Int): FrameDescriptor {
        val builder = FrameDescriptor.newBuilder()
        repeat(slotCount) {
            builder.addSlot(FrameSlotKind.Illegal, null, null)
        }
        return builder.build()
    }

    private class EvalNode(
        lang: BridjeLanguage,
        frameDescriptor: FrameDescriptor,
        @field:Child private var node: BridjeNode
    ) : RootNode(lang, frameDescriptor) {
        override fun execute(frame: VirtualFrame) = node.execute(frame)
    }

    @TruffleBoundary
    private fun evalExpr(expr: ValueExpr, slotCount: Int): Any? {
        val frameDescriptor = buildFrameDescriptor(slotCount)
        val emitter = Emitter(lang)
        val node = emitter.emitExpr(expr)
        return EvalNode(lang, frameDescriptor, node).callTarget.call(this)
    }

    @TruffleBoundary
    private fun List<Form>.evalForms(ctx: BridjeContext, nsEnv: NsEnv): Pair<NsEnv, Any?> {
        var nsEnv = nsEnv
        val errors = mutableListOf<Analyser.Error>()

        val res = fold(null as Any?) { _, form ->
            val analyser = Analyser(ctx, nsEnv)

            when (val expr = analyser.analyse(form)) {
                is TopLevelDo -> {
                    val (newNsEnv, res) = expr.forms.evalForms(ctx, nsEnv)
                    nsEnv = newNsEnv
                    res
                }

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
                    nsEnv = nsEnv.def(expr.name, key)
                    key
                }

                is ValueExpr -> evalExpr(expr, analyser.slotCount)

                is AnalyserErrors -> {
                    errors.addAll(expr.errors)
                    null
                }
            }
        }

        return when {
            errors.isEmpty() -> Pair(nsEnv, res)
            else -> throw Analyser.Errors(errors)
        }
    }

    @TruffleBoundary
    private fun doExecute(initialNsEnv: NsEnv): Any? {
        val ctx = BridjeContext.get(this)

        val (nsEnv, result) = forms.evalForms(ctx, initialNsEnv)

        if (nsDecl == null) return result

        ctx.updateGlobalEnv { globalEnv ->
            val invalidated = globalEnv.invalidateNamespace(nsDecl.name)
            invalidated.withNamespace(nsDecl.name, nsEnv)
        }

        // Update brjCore if this is brj:core namespace
        if (nsDecl.name == "brj:core") {
            ctx.brjCore = nsEnv
        }

        return nsEnv
    }

    override fun execute(frame: VirtualFrame): Any? {
        val initialNsEnv = when {
            // brj:core starts with Kotlin builtins
            nsDecl?.name == "brj:core" -> NsEnv.withBuiltins(lang).copy(
                imports = nsDecl.imports,
                nsDecl = nsDecl,
                source = source
            )
            nsDecl != null -> nsDecl.resolve(frame)
            else -> NsEnv()
        }
        return doExecute(initialNsEnv)
    }
}
