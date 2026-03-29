package brj

import brj.analyser.*
import brj.nodes.*
import brj.runtime.BridjeFunction
import brj.runtime.HostClass
import brj.runtime.BridjeNull
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

class Emitter(private val language: BridjeLanguage) {
    fun emitExpr(expr: ValueExpr, effectEnv: Map<GlobalVar, BridjeNode> = emptyMap()): BridjeNode = when (expr) {
        is NilExpr -> NilNode(expr.loc)
        is BoolExpr -> BoolNode(expr.value, expr.loc)
        is IntExpr -> IntNode(expr.value, expr.loc)
        is DoubleExpr -> DoubleNode(expr.value, expr.loc)
        is BigIntExpr -> BigIntNode(expr.value, expr.loc)
        is BigDecExpr -> BigDecNode(expr.value, expr.loc)
        is StringExpr -> StringNode(expr.value, expr.loc)
        is VectorExpr -> VectorNodeGen.create(expr.loc, ExecuteArrayNode(expr.els.map { emitExpr(it, effectEnv) }.toTypedArray(), expr.loc))
        is SetExpr -> TODO()
        is RecordExpr -> RecordNodeGen.create(
            expr.fields.map { it.first }.toTypedArray(),
            expr.loc,
            ExecuteArrayNode(expr.fields.map { emitExpr(it.second, effectEnv) }.toTypedArray(), expr.loc)
        )
        is LocalVarExpr -> ReadLocalNode(expr.localVar.slot, expr.loc)
        is CapturedVarExpr -> ReadCapturedVarNode(expr.captureIndex, expr.loc)
        is GlobalVarExpr -> GlobalVarNode(expr.globalVar, expr.loc)
        is TruffleObjectExpr -> TruffleObjectNode(expr.value, expr.loc)
        is HostStaticMethodExpr -> HostStaticMethodNode(expr.hostClass, expr.methodName, expr.loc)
        is HostConstructorExpr -> TruffleObjectNode(HostClass(expr.hostClass), expr.loc)
        is QuoteExpr -> TruffleObjectNode(expr.form, expr.loc)
        is LetExpr -> LetNode(expr.localVar.slot, emitExpr(expr.bindingExpr, effectEnv), emitExpr(expr.bodyExpr, effectEnv), expr.loc)
        is FnExpr -> emitFn(expr, effectEnv)
        is CallExpr -> emitCall(expr, effectEnv)
        is DoExpr -> DoNode(expr.sideEffects.map { emitExpr(it, effectEnv) }.toTypedArray(), emitExpr(expr.result, effectEnv), expr.loc)
        is IfExpr -> IfNode(emitExpr(expr.predExpr, effectEnv), emitExpr(expr.thenExpr, effectEnv), emitExpr(expr.elseExpr, effectEnv), expr.loc)
        is CaseExpr -> emitCase(expr, effectEnv)
        is RecordSetExpr -> RecordSetNode(expr.key, emitExpr(expr.recordExpr, effectEnv), emitExpr(expr.valueExpr, effectEnv), expr.loc)
        is EffectVarExpr -> effectEnv[expr.effectVar] ?: GlobalVarNode(expr.effectVar, expr.loc)
        is WithFxExpr -> {
            val newEnv = effectEnv + expr.bindings.associate { (gv, implExpr) -> gv to emitExpr(implExpr, effectEnv) }
            emitExpr(expr.bodyExpr, newEnv)
        }

        is ErrorValueExpr -> error("analyser error: ${expr.message}")
    }

    private fun emitCall(expr: CallExpr, effectEnv: Map<GlobalVar, BridjeNode>): BridjeNode {
        val fnNode = emitExpr(expr.fnExpr, effectEnv)
        val argNodes = expr.argExprs.map { emitExpr(it, effectEnv) }.toTypedArray()

        val calleeEffects = when (val fnExpr = expr.fnExpr) {
            is GlobalVarExpr -> fnExpr.globalVar.effects
            else -> emptyList()
        }

        if (calleeEffects.isNotEmpty()) {
            val effectNodes = calleeEffects.map { effectVar ->
                effectEnv[effectVar] ?: GlobalVarNode(effectVar, expr.loc)
            }.toTypedArray()
            val stage1 = InvokeNode(fnNode, effectNodes, expr.loc)
            return InvokeNode(stage1, argNodes, expr.loc)
        }

        return InvokeNode(fnNode, argNodes, expr.loc)
    }

    private fun emitCase(expr: CaseExpr, effectEnv: Map<GlobalVar, BridjeNode>): CaseNode {
        val scrutineeNode = emitExpr(expr.scrutinee, effectEnv)
        val branchNodes = expr.branches.map { branch ->
            when (val pattern = branch.pattern) {
                is TagPattern -> TagBranchNode(
                    pattern.tagValue,
                    pattern.bindings.map { it.slot }.toIntArray(),
                    emitExpr(branch.bodyExpr, effectEnv),
                    branch.loc
                )
                is DefaultPattern -> DefaultBranchNode(
                    emitExpr(branch.bodyExpr, effectEnv),
                    branch.loc
                )
                is NilPattern -> NilBranchNode(
                    emitExpr(branch.bodyExpr, effectEnv),
                    branch.loc
                )
                is CatchAllBindingPattern -> CatchAllBindingBranchNode(
                    pattern.binding.slot,
                    emitExpr(branch.bodyExpr, effectEnv),
                    branch.loc
                )
            }
        }.toTypedArray()
        return CaseNode(scrutineeNode, branchNodes, expr.loc)
    }

    private fun emitFn(expr: FnExpr, effectEnv: Map<GlobalVar, BridjeNode>): BridjeNode {
        // Inner fns have their own frame.
        // Effect env is cleared: inner fns that need effects get them via their own two-stage calling convention.
        val bodyNode = emitExpr(expr.bodyExpr, effectEnv = emptyMap())
        val fdBuilder = FrameDescriptor.newBuilder()
        repeat(expr.slotCount) {
            fdBuilder.addSlot(FrameSlotKind.Illegal, null, null)
        }
        val rootNode = FnRootNode(language, fdBuilder.build(), expr.params.size, expr.captures.isNotEmpty(), bodyNode)

        return if (expr.captures.isEmpty()) {
            FnNode(BridjeFunction(rootNode.callTarget), expr.loc)
        } else {
            val captureSources = expr.captures.map { it.source }.toTypedArray()
            ClosureFnNode(rootNode.callTarget, captureSources, expr.loc)
        }
    }
}
