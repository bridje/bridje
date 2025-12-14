package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

/**
 * Abstract base class for binary operator builtin functions.
 * Each operator is implemented as a separate subclass using Truffle DSL specializations.
 */
abstract class BuiltinBinaryOperatorNode(language: BridjeLanguage) : RootNode(language) {

    override fun execute(frame: VirtualFrame): Any? {
        val args = frame.arguments
        if (args.size != 2) {
            throw IllegalArgumentException("Binary operator requires exactly 2 arguments, got ${args.size}")
        }

        val left = args[0]
        val right = args[1]

        return executeOp(left, right)
    }

    protected abstract fun executeOp(left: Any?, right: Any?): Any?
}

// Arithmetic operators

abstract class AddNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun add(left: Long, right: Long): Long = left + right

    @Specialization
    protected fun add(left: Long, right: Double): Double = left + right

    @Specialization
    protected fun add(left: Double, right: Long): Double = left + right

    @Specialization
    protected fun add(left: Double, right: Double): Double = left + right
}

abstract class SubNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun sub(left: Long, right: Long): Long = left - right

    @Specialization
    protected fun sub(left: Long, right: Double): Double = left - right

    @Specialization
    protected fun sub(left: Double, right: Long): Double = left - right

    @Specialization
    protected fun sub(left: Double, right: Double): Double = left - right
}

abstract class MulNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun mul(left: Long, right: Long): Long = left * right

    @Specialization
    protected fun mul(left: Long, right: Double): Double = left * right

    @Specialization
    protected fun mul(left: Double, right: Long): Double = left * right

    @Specialization
    protected fun mul(left: Double, right: Double): Double = left * right
}

abstract class DivNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization(rewriteOn = [ArithmeticException::class])
    protected fun divLong(left: Long, right: Long): Long {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization(rewriteOn = [ArithmeticException::class])
    protected fun divLongDouble(left: Long, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization(rewriteOn = [ArithmeticException::class])
    protected fun divDoubleLong(left: Double, right: Long): Double {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization(rewriteOn = [ArithmeticException::class])
    protected fun divDouble(left: Double, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }
}

// Comparison operators

abstract class EqNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun eq(left: Long, right: Long): Boolean = left == right

    @Specialization
    protected fun eq(left: Long, right: Double): Boolean = left.toDouble() == right

    @Specialization
    protected fun eq(left: Double, right: Long): Boolean = left == right.toDouble()

    @Specialization
    protected fun eq(left: Double, right: Double): Boolean = left == right

    @Specialization
    protected fun eq(left: Boolean, right: Boolean): Boolean = left == right

    @Specialization
    protected fun eqGeneric(left: Any?, right: Any?): Boolean = left == right
}

abstract class NeqNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun neq(left: Long, right: Long): Boolean = left != right

    @Specialization
    protected fun neq(left: Long, right: Double): Boolean = left.toDouble() != right

    @Specialization
    protected fun neq(left: Double, right: Long): Boolean = left != right.toDouble()

    @Specialization
    protected fun neq(left: Double, right: Double): Boolean = left != right

    @Specialization
    protected fun neq(left: Boolean, right: Boolean): Boolean = left != right

    @Specialization
    protected fun neqGeneric(left: Any?, right: Any?): Boolean = left != right
}

abstract class LtNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun lt(left: Long, right: Long): Boolean = left < right

    @Specialization
    protected fun lt(left: Long, right: Double): Boolean = left < right

    @Specialization
    protected fun lt(left: Double, right: Long): Boolean = left < right

    @Specialization
    protected fun lt(left: Double, right: Double): Boolean = left < right
}

abstract class GtNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun gt(left: Long, right: Long): Boolean = left > right

    @Specialization
    protected fun gt(left: Long, right: Double): Boolean = left > right

    @Specialization
    protected fun gt(left: Double, right: Long): Boolean = left > right

    @Specialization
    protected fun gt(left: Double, right: Double): Boolean = left > right
}

abstract class LteNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun lte(left: Long, right: Long): Boolean = left <= right

    @Specialization
    protected fun lte(left: Long, right: Double): Boolean = left <= right

    @Specialization
    protected fun lte(left: Double, right: Long): Boolean = left <= right

    @Specialization
    protected fun lte(left: Double, right: Double): Boolean = left <= right
}

abstract class GteNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun gte(left: Long, right: Long): Boolean = left >= right

    @Specialization
    protected fun gte(left: Long, right: Double): Boolean = left >= right

    @Specialization
    protected fun gte(left: Double, right: Long): Boolean = left >= right

    @Specialization
    protected fun gte(left: Double, right: Double): Boolean = left >= right
}
