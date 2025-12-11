package brj

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

fun emitExpr(expr: Expr): BridjeNode = when (expr) {
    is IntExpr -> IntNode(expr.value)
    is DoubleExpr -> DoubleNode(expr.value)
    is BigIntExpr -> BigIntNode(expr.value)
    is BigDecExpr -> BigDecNode(expr.value)
    is StringExpr -> StringNode(expr.value)
    is VectorExpr -> TODO()
    is SetExpr -> TODO()
    is MapExpr -> TODO()
}
