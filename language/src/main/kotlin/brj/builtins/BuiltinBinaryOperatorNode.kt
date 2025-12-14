package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.RootNode

/**
 * Abstract base class for binary operator builtin functions.
 * Each operator is implemented as a separate subclass using Truffle DSL specializations.
 */
abstract class BuiltinBinaryOperatorNode(language: BridjeLanguage) : RootNode(language)

// Arithmetic operators

abstract class AddNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Long = left + right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Double = left + right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Double = left + right

    @Specialization
    protected fun executeDD(left: Double, right: Double): Double = left + right
}

abstract class SubNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Long = left - right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Double = left - right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Double = left - right

    @Specialization
    protected fun executeDD(left: Double, right: Double): Double = left - right
}

abstract class MulNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Long = left * right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Double = left * right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Double = left * right

    @Specialization
    protected fun executeDD(left: Double, right: Double): Double = left * right
}

abstract class DivNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Long {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization
    protected fun executeLD(left: Long, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization
    protected fun executeDL(left: Double, right: Long): Double {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    @Specialization
    protected fun executeDD(left: Double, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }
}

// Comparison operators

abstract class EqNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Boolean = left == right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Boolean = left.toDouble() == right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Boolean = left == right.toDouble()

    @Specialization
    protected fun executeDD(left: Double, right: Double): Boolean = left == right

    @Specialization
    protected fun executeBB(left: Boolean, right: Boolean): Boolean = left == right

    @Specialization
    protected fun executeGeneric(left: Any?, right: Any?): Boolean = left == right
}

abstract class NeqNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Boolean = left != right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Boolean = left.toDouble() != right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Boolean = left != right.toDouble()

    @Specialization
    protected fun executeDD(left: Double, right: Double): Boolean = left != right

    @Specialization
    protected fun executeBB(left: Boolean, right: Boolean): Boolean = left != right

    @Specialization
    protected fun executeGeneric(left: Any?, right: Any?): Boolean = left != right
}

abstract class LtNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Boolean = left < right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Boolean = left < right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Boolean = left < right

    @Specialization
    protected fun executeDD(left: Double, right: Double): Boolean = left < right
}

abstract class GtNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Boolean = left > right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Boolean = left > right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Boolean = left > right

    @Specialization
    protected fun executeDD(left: Double, right: Double): Boolean = left > right
}

abstract class LteNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Boolean = left <= right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Boolean = left <= right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Boolean = left <= right

    @Specialization
    protected fun executeDD(left: Double, right: Double): Boolean = left <= right
}

abstract class GteNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected fun executeLL(left: Long, right: Long): Boolean = left >= right

    @Specialization
    protected fun executeLD(left: Long, right: Double): Boolean = left >= right

    @Specialization
    protected fun executeDL(left: Double, right: Long): Boolean = left >= right

    @Specialization
    protected fun executeDD(left: Double, right: Double): Boolean = left >= right
}
