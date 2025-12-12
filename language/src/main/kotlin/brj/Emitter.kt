package brj

import brj.nodes.*
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.strings.TruffleString
import java.math.BigDecimal
import java.math.BigInteger

@TypeSystemReference(BridjeTypes::class)
abstract class BridjeNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any?

    @Throws(UnexpectedResultException::class)
    open fun executeBoolean(frame: VirtualFrame): Boolean {
        return BridjeTypesGen.expectBoolean(execute(frame))
    }
}

class IntNode(private val value: Long) : BridjeNode() {
    override fun execute(frame: VirtualFrame) = value
}

class DoubleNode(private val value: Double) : BridjeNode() {
    override fun execute(frame: VirtualFrame) = value
}

class BigIntNode(private val value: BigInteger) : BridjeNode() {
    override fun execute(frame: VirtualFrame) = TODO("BigInt interop")
}

class BigDecNode(private val value: BigDecimal) : BridjeNode() {
    override fun execute(frame: VirtualFrame) = TODO("BigDec interop")
}

class StringNode(value: String) : BridjeNode() {
    private val string: TruffleString = TruffleString.fromConstant(value, TruffleString.Encoding.UTF_8)

    override fun execute(frame: VirtualFrame) = string
}

class BoolNode(private val value: Boolean) : BridjeNode() {
    override fun execute(frame: VirtualFrame) = value
}

class Emitter(private val language: BridjeLanguage) {
    fun emitExpr(expr: Expr): BridjeNode = when (expr) {
        is BoolExpr -> BoolNode(expr.value)
        is IntExpr -> IntNode(expr.value)
        is DoubleExpr -> DoubleNode(expr.value)
        is BigIntExpr -> BigIntNode(expr.value)
        is BigDecExpr -> BigDecNode(expr.value)
        is StringExpr -> StringNode(expr.value)
        is VectorExpr -> VectorNodeGen.create(ExecuteArrayNode(expr.els.map { emitExpr(it) }.toTypedArray()))
        is SetExpr -> TODO()
        is MapExpr -> TODO()
        is LocalVarExpr -> ReadLocalNode(expr.localVar.slot)
        is LetExpr -> LetNode(expr.localVar.slot, emitExpr(expr.bindingExpr), emitExpr(expr.bodyExpr))
        is FnExpr -> emitFn(expr)
        is CallExpr -> InvokeNode(emitExpr(expr.fnExpr), expr.argExprs.map { emitExpr(it) }.toTypedArray())
        is DoExpr -> DoNode(expr.sideEffects.map { emitExpr(it) }.toTypedArray(), emitExpr(expr.result))
        is IfExpr -> IfNode(emitExpr(expr.predExpr), emitExpr(expr.thenExpr), emitExpr(expr.elseExpr))
    }

    private fun emitFn(expr: FnExpr): FnNode {
        val bodyNode = emitExpr(expr.bodyExpr)
        val fdBuilder = FrameDescriptor.newBuilder()
        repeat(expr.params.size) {
            fdBuilder.addSlot(FrameSlotKind.Illegal, null, null)
        }
        val rootNode = FnRootNode(language, fdBuilder.build(), expr.params.size, bodyNode)
        return FnNode(BridjeFunction(rootNode.callTarget))
    }
}
