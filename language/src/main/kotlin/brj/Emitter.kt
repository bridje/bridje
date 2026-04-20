package brj

import brj.analyser.*
import brj.effects.collectEffectfulCallees
import brj.effects.inferEffects
import brj.nodes.*
import brj.runtime.BridjeContext
import brj.runtime.BridjeFxMap
import brj.runtime.BridjeFunction
import brj.runtime.HostClass
import brj.runtime.BridjeNull
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.strings.TruffleString
import java.math.BigDecimal
import java.math.BigInteger

@TypeSystemReference(BridjeTypes::class)
abstract class BridjeNode(
    private val loc: SourceSection? = null
) : Node() {

    override fun getSourceSection() = loc

    abstract fun execute(frame: VirtualFrame): Any?

    @Throws(UnexpectedResultException::class)
    open fun executeBoolean(frame: VirtualFrame): Boolean = BridjeTypesGen.expectBoolean(execute(frame))
}

class IntNode(private val value: Long, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame) = value
}

class DoubleNode(private val value: Double, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame) = value
}

class BigIntNode(private val value: BigInteger, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame) = TODO("BigInt interop")
}

class BigDecNode(private val value: BigDecimal, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame) = TODO("BigDec interop")
}

class StringNode(value: String, loc: SourceSection? = null) : BridjeNode(loc) {
    private val string: TruffleString = TruffleString.fromConstant(value, TruffleString.Encoding.UTF_8)

    override fun execute(frame: VirtualFrame) = string
}

class NilNode(loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any = BridjeNull
}

class BoolNode(private val value: Boolean, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame) = value
}

class TruffleObjectNode(private val value: Any, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame) = value
}

class HostStaticMethodNode(
    private val hostClass: TruffleObject,
    private val methodName: String,
    loc: SourceSection? = null
) : BridjeNode(loc) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any? = interop.readMember(hostClass, methodName)
}

/** Factory that produces a fresh BridjeNode each time — avoids Truffle's one-parent rule. */
sealed interface NodeSource {
    fun create(): BridjeNode
    fun captureSource(): CaptureSource
}

data class LocalNodeSource(val slot: Int) : NodeSource {
    override fun create(): BridjeNode = ReadLocalNode(slot)
    override fun captureSource() = FrameSlotCapture(slot)
}

data class CapturedNodeSource(val captureIndex: Int) : NodeSource {
    override fun create(): BridjeNode = ReadCapturedVarNode(captureIndex)
    override fun captureSource() = TransitiveCapture(captureIndex)
}

class Emitter(private val language: BridjeLanguage, private val ctx: BridjeContext) {
    var nextSlot: Int = 0

    fun allocSlot(): Int = nextSlot++

    /**
     * @param fxSource How to read the fx map in the current scope (null = no effects in scope).
     * @param preApplied Map of effectful callees to sources that read their pre-applied closures.
     */
    fun emitExpr(
        expr: ValueExpr,
        fxSource: NodeSource? = null,
        preApplied: Map<GlobalVar, NodeSource> = emptyMap()
    ): BridjeNode = when (expr) {
        is NilExpr -> NilNode(expr.loc)
        is BoolExpr -> BoolNode(expr.value, expr.loc)
        is IntExpr -> IntNode(expr.value, expr.loc)
        is DoubleExpr -> DoubleNode(expr.value, expr.loc)
        is BigIntExpr -> BigIntNode(expr.value, expr.loc)
        is BigDecExpr -> BigDecNode(expr.value, expr.loc)
        is StringExpr -> StringNode(expr.value, expr.loc)
        is VectorExpr -> VectorNode(ExecuteCollNode(expr.els.map { emitExpr(it, fxSource, preApplied) }.toTypedArray(), expr.loc), expr.loc)
        is SetExpr -> TODO()
        is RecordExpr -> RecordNode(
            expr.fields.map { it.first }.toTypedArray(),
            ExecuteCollNode(expr.fields.map { emitExpr(it.second, fxSource, preApplied) }.toTypedArray(), expr.loc),
            expr.loc
        )
        is LocalVarExpr -> ReadLocalNode(expr.localVar.slot, expr.loc)
        is CapturedVarExpr -> ReadCapturedVarNode(expr.captureIndex, expr.loc)
        is GlobalVarExpr -> GlobalVarNode(expr.globalVar, expr.loc)
        is TruffleObjectExpr -> TruffleObjectNode(expr.value, expr.loc)
        is HostStaticMethodExpr -> HostStaticMethodNode(expr.hostClass, expr.methodName, expr.loc)
        is HostConstructorExpr -> TruffleObjectNode(HostClass(expr.hostClass), expr.loc)
        is QuoteExpr -> TruffleObjectNode(expr.form, expr.loc)
        is LetExpr -> LetNode(expr.localVar.slot, emitExpr(expr.bindingExpr, fxSource, preApplied), emitExpr(expr.bodyExpr, fxSource, preApplied), expr.loc)
        is FnExpr -> emitFn(expr, fxSource, preApplied)
        is CallExpr -> emitCall(expr, fxSource, preApplied)
        is DoExpr -> DoNode(expr.sideEffects.map { emitExpr(it, fxSource, preApplied) }.toTypedArray(), emitExpr(expr.result, fxSource, preApplied), expr.loc)
        is IfExpr -> IfNode(emitExpr(expr.predExpr, fxSource, preApplied), emitExpr(expr.thenExpr, fxSource, preApplied), emitExpr(expr.elseExpr, fxSource, preApplied), expr.loc)
        is CaseExpr -> emitCase(expr, fxSource, preApplied)
        is TryCatchExpr -> emitTryCatch(expr, fxSource, preApplied)
        is RecordSetExpr -> RecordSetNode(expr.key, emitExpr(expr.recordExpr, fxSource, preApplied), emitExpr(expr.valueExpr, fxSource, preApplied), expr.loc)
        is RecordUpdateExpr -> RecordUpdateNode(
            expr.fields.map { it.first }.toTypedArray(),
            emitExpr(expr.recordExpr, fxSource, preApplied),
            expr.fields.map { emitExpr(it.second, fxSource, preApplied) }.toTypedArray(),
            expr.loc
        )
        is EffectVarExpr -> {
            if (fxSource != null) ReadFxMapEntryNode(fxSource.create(), expr.effectVar, expr.loc)
            else GlobalVarNode(expr.effectVar, expr.loc)
        }
        is WithFxExpr -> emitWithFx(expr, fxSource, preApplied)
        is LoopExpr -> emitLoop(expr, fxSource, preApplied)
        is RecurExpr -> RecurNode(expr.argExprs.map { emitExpr(it, fxSource, preApplied) }.toTypedArray(), expr.loc)
        is LangExpr -> emitLang(expr)

        is ErrorValueExpr -> error("analyser error: ${expr.message}")
    }

    private fun emitLang(expr: LangExpr): BridjeNode {
        val source = Source.newBuilder(expr.language, expr.code, "lang-${expr.language}").build()
        val value = ctx.truffleEnv.parsePublic(source).call()
        return LangNode(value, expr.loc)
    }

    private fun emitCall(expr: CallExpr, fxSource: NodeSource?, preApplied: Map<GlobalVar, NodeSource>): BridjeNode {
        val argNodes = expr.argExprs.map { emitExpr(it, fxSource, preApplied) }.toTypedArray()

        val callee = (expr.fnExpr as? GlobalVarExpr)?.globalVar

        // Use pre-applied closure if available (single invoke).
        if (callee != null && callee in preApplied) {
            return InvokeNode(preApplied[callee]!!.create(), argNodes, expr.loc)
        }

        val fnNode = emitExpr(expr.fnExpr, fxSource, preApplied)

        // Two-stage fallback: callee(fx)(args).
        if (callee != null && callee.effects.isNotEmpty()) {
            val fx = fxSource?.create() ?: TruffleObjectNode(BridjeFxMap.EMPTY, expr.loc)
            val stage1 = InvokeNode(fnNode, arrayOf(fx), expr.loc)
            return InvokeNode(stage1, argNodes, expr.loc)
        }

        return InvokeNode(fnNode, argNodes, expr.loc)
    }

    private fun emitWithFx(expr: WithFxExpr, fxSource: NodeSource?, preApplied: Map<GlobalVar, NodeSource>): BridjeNode {
        // Build new fx map: base + overrides.
        val baseFx = fxSource?.create() ?: TruffleObjectNode(BridjeFxMap.EMPTY, expr.loc)
        val keys = expr.bindings.map { it.first }.toTypedArray()
        val valueNodes = expr.bindings.map { emitExpr(it.second, fxSource, preApplied) }.toTypedArray()
        val newFxMapNode = BuildFxMapNode(baseFx, keys, valueNodes, expr.loc)

        // Let-bind the new fx map into a slot.
        val fxSlot = allocSlot()
        val newFxSource = LocalNodeSource(fxSlot)

        // Pre-apply effectful callees from the new fx map.
        val callees = expr.bodyExpr.collectEffectfulCallees().toList()
        val calleeSlots = callees.map { it to allocSlot() }
        val newPreApplied = if (calleeSlots.isNotEmpty()) {
            preApplied + calleeSlots.associate { (gv, slot) -> gv to LocalNodeSource(slot) as NodeSource }
        } else {
            preApplied
        }

        val bodyNode = emitExpr(expr.bodyExpr, newFxSource, newPreApplied)

        // Wrap body in LetNodes for pre-applied callees (innermost), then fx map (outermost).
        var result = bodyNode
        for ((callee, slot) in calleeSlots.reversed()) {
            val preApplyNode = InvokeNode(
                GlobalVarNode(callee, expr.loc),
                arrayOf(ReadLocalNode(fxSlot, expr.loc) as BridjeNode),
                expr.loc
            )
            result = LetNode(slot, preApplyNode, result, expr.loc)
        }
        result = LetNode(fxSlot, newFxMapNode, result, expr.loc)

        return result
    }

    private fun emitCase(expr: CaseExpr, fxSource: NodeSource?, preApplied: Map<GlobalVar, NodeSource>): CaseNode {
        val scrutineeNode = emitExpr(expr.scrutinee, fxSource, preApplied)
        val branchNodes = expr.branches.map { branch ->
            when (val pattern = branch.pattern) {
                is TagPattern -> TagBranchNode(
                    pattern.tagValue,
                    pattern.bindings.map { it.slot }.toIntArray(),
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
                is DefaultPattern -> DefaultBranchNode(
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
                is NilPattern -> NilBranchNode(
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
                is CatchAllBindingPattern -> CatchAllBindingBranchNode(
                    pattern.binding.slot,
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
            }
        }.toTypedArray()
        return CaseNode(scrutineeNode, branchNodes, expr.loc)
    }

    private fun emitTryCatch(expr: TryCatchExpr, fxSource: NodeSource?, preApplied: Map<GlobalVar, NodeSource>): BridjeNode {
        val bodyNode = emitExpr(expr.bodyExpr, fxSource, preApplied)
        val branchNodes = expr.catchBranches.map { branch ->
            when (val pattern = branch.pattern) {
                is TagPattern -> TagBranchNode(
                    pattern.tagValue,
                    pattern.bindings.map { it.slot }.toIntArray(),
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
                is DefaultPattern -> DefaultBranchNode(
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
                is NilPattern -> NilBranchNode(
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
                is CatchAllBindingPattern -> CatchAllBindingBranchNode(
                    pattern.binding.slot,
                    emitExpr(branch.bodyExpr, fxSource, preApplied),
                    branch.loc
                )
            }
        }.toTypedArray()
        val finallyNode = expr.finallyExpr?.let { emitExpr(it, fxSource, preApplied) }
        return TryCatchNode(bodyNode, branchNodes, finallyNode, expr.loc)
    }

    private fun emitLoop(expr: LoopExpr, fxSource: NodeSource?, preApplied: Map<GlobalVar, NodeSource>): BridjeNode {
        val slots = expr.bindings.map { it.first.slot }.toIntArray()
        val initNodes = expr.bindings.map { emitExpr(it.second, fxSource, preApplied) }.toTypedArray()
        val resultSlot = allocSlot()
        val bodyNode = emitExpr(expr.bodyExpr, fxSource, preApplied)
        val repeatingNode = LoopRepeatingNode(slots, resultSlot, bodyNode)
        val loopNode = Truffle.getRuntime().createLoopNode(repeatingNode)
        return LoopBridjeNode(slots, resultSlot, initNodes, loopNode, expr.loc)
    }

    private fun emitFn(expr: FnExpr, fxSource: NodeSource?, preApplied: Map<GlobalVar, NodeSource>): BridjeNode {
        // Inner fns capture the fx map and any pre-applied callees they need.
        val analyserCaptures = expr.captures.map { it.source }
        val extraCaptures = mutableListOf<CaptureSource>()

        // Capture the fx map if the inner fn or anything inside it uses effects.
        val bodyUsesEffects = expr.bodyExpr.inferEffects().isNotEmpty()
        var innerFxSource: NodeSource? = null
        if (fxSource != null && bodyUsesEffects) {
            val captureIdx = analyserCaptures.size + extraCaptures.size
            extraCaptures.add(fxSource.captureSource())
            innerFxSource = CapturedNodeSource(captureIdx)
        }

        // Capture pre-applied callees that the inner fn body needs.
        val neededCallees = expr.bodyExpr.collectEffectfulCallees()
        val innerPreApplied = mutableMapOf<GlobalVar, NodeSource>()
        for (callee in neededCallees) {
            val source = preApplied[callee] ?: continue
            val captureIdx = analyserCaptures.size + extraCaptures.size
            extraCaptures.add(source.captureSource())
            innerPreApplied[callee] = CapturedNodeSource(captureIdx)
        }

        val allCaptureSources = (analyserCaptures + extraCaptures).toTypedArray()
        val hasCapturedValues = allCaptureSources.isNotEmpty()

        val innerEmitter = Emitter(language, ctx)
        innerEmitter.nextSlot = expr.slotCount
        val rawBodyNode = innerEmitter.emitExpr(expr.bodyExpr, innerFxSource, innerPreApplied)

        val paramSlots = expr.params.map { it.slot }.toIntArray()
        val resultSlot = innerEmitter.allocSlot()
        val repeatingNode = LoopRepeatingNode(paramSlots, resultSlot, rawBodyNode)
        val loopNode = Truffle.getRuntime().createLoopNode(repeatingNode)
        val bodyNode = LoopBridjeNode(paramSlots, resultSlot, emptyArray(), loopNode, expr.loc)

        val fdBuilder = FrameDescriptor.newBuilder()
        repeat(innerEmitter.nextSlot) {
            fdBuilder.addSlot(FrameSlotKind.Illegal, null, null)
        }
        val rootNode = FnRootNode(language, fdBuilder.build(), expr.params.size, hasCapturedValues, bodyNode)

        return if (!hasCapturedValues) {
            FnNode(BridjeFunction(rootNode.callTarget), expr.loc)
        } else {
            ClosureFnNode(rootNode.callTarget, allCaptureSources, expr.loc)
        }
    }
}
