package brj

import brj.nodes.*
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.strings.TruffleString
import java.math.BigDecimal
import java.math.BigInteger

abstract class BridjeNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any?
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

class Emitter(private val language: BridjeLanguage) {
    fun emitExpr(expr: Expr): BridjeNode = when (expr) {
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
