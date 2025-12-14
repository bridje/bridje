package brj.builtins

import brj.BridjeLanguage
import brj.BridjeTypes
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode

/**
 * Node that reads an argument from frame.arguments at a given index.
 */
class ReadArgumentNode(private val index: Int) : Node() {
    fun execute(frame: VirtualFrame): Any? = frame.arguments[index]
}

/**
 * Abstract base class for binary operator nodes.
 * Uses @NodeChild to declare left and right operands which are read from frame arguments.
 */
@TypeSystemReference(BridjeTypes::class)
@NodeChild(value = "left", type = ReadArgumentNode::class)
@NodeChild(value = "right", type = ReadArgumentNode::class)
abstract class BinaryOpNode(language: BridjeLanguage) : RootNode(language)

// Arithmetic operators

abstract class AddNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doAdd(left: Long, right: Long): Long = left + right

    @Specialization
    protected fun doAdd(left: Long, right: Double): Double = left + right

    @Specialization
    protected fun doAdd(left: Double, right: Long): Double = left + right

    @Specialization
    protected fun doAdd(left: Double, right: Double): Double = left + right
}

abstract class SubNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doSub(left: Long, right: Long): Long = left - right

    @Specialization
    protected fun doSub(left: Long, right: Double): Double = left - right

    @Specialization
    protected fun doSub(left: Double, right: Long): Double = left - right

    @Specialization
    protected fun doSub(left: Double, right: Double): Double = left - right
}

abstract class MulNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doMul(left: Long, right: Long): Long = left * right

    @Specialization
    protected fun doMul(left: Long, right: Double): Double = left * right

    @Specialization
    protected fun doMul(left: Double, right: Long): Double = left * right

    @Specialization
    protected fun doMul(left: Double, right: Double): Double = left * right
}

abstract class DivNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doDiv(left: Long, right: Long): Long {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization
    protected fun doDiv(left: Long, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization
    protected fun doDiv(left: Double, right: Long): Double {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization
    protected fun doDiv(left: Double, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }
}

// Comparison operators

abstract class EqNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doEq(left: Long, right: Long): Boolean = left == right

    @Specialization
    protected fun doEq(left: Long, right: Double): Boolean = left.toDouble() == right

    @Specialization
    protected fun doEq(left: Double, right: Long): Boolean = left == right.toDouble()

    @Specialization
    protected fun doEq(left: Double, right: Double): Boolean = left == right

    @Specialization
    protected fun doEq(left: Boolean, right: Boolean): Boolean = left == right

    @Specialization
    protected fun doEq(left: Any?, right: Any?): Boolean = left == right
}

abstract class NeqNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doNeq(left: Long, right: Long): Boolean = left != right

    @Specialization
    protected fun doNeq(left: Long, right: Double): Boolean = left.toDouble() != right

    @Specialization
    protected fun doNeq(left: Double, right: Long): Boolean = left != right.toDouble()

    @Specialization
    protected fun doNeq(left: Double, right: Double): Boolean = left != right

    @Specialization
    protected fun doNeq(left: Boolean, right: Boolean): Boolean = left != right

    @Specialization
    protected fun doNeq(left: Any?, right: Any?): Boolean = left != right
}

abstract class LtNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doLt(left: Long, right: Long): Boolean = left < right

    @Specialization
    protected fun doLt(left: Long, right: Double): Boolean = left < right

    @Specialization
    protected fun doLt(left: Double, right: Long): Boolean = left < right

    @Specialization
    protected fun doLt(left: Double, right: Double): Boolean = left < right
}

abstract class GtNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doGt(left: Long, right: Long): Boolean = left > right

    @Specialization
    protected fun doGt(left: Long, right: Double): Boolean = left > right

    @Specialization
    protected fun doGt(left: Double, right: Long): Boolean = left > right

    @Specialization
    protected fun doGt(left: Double, right: Double): Boolean = left > right
}

abstract class LteNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doLte(left: Long, right: Long): Boolean = left <= right

    @Specialization
    protected fun doLte(left: Long, right: Double): Boolean = left <= right

    @Specialization
    protected fun doLte(left: Double, right: Long): Boolean = left <= right

    @Specialization
    protected fun doLte(left: Double, right: Double): Boolean = left <= right
}

abstract class GteNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Specialization
    protected fun doGte(left: Long, right: Long): Boolean = left >= right

    @Specialization
    protected fun doGte(left: Long, right: Double): Boolean = left >= right

    @Specialization
    protected fun doGte(left: Double, right: Long): Boolean = left >= right

    @Specialization
    protected fun doGte(left: Double, right: Double): Boolean = left >= right
}
