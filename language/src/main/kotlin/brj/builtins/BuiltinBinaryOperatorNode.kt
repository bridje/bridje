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
    protected abstract fun executeLL(left: Long, right: Long): Long

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Double

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Double

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Double
}

class AddNodeImpl(language: BridjeLanguage) : AddNode(language) {
    override fun executeLL(left: Long, right: Long): Long = left + right
    override fun executeLD(left: Long, right: Double): Double = left + right
    override fun executeDL(left: Double, right: Long): Double = left + right
    override fun executeDD(left: Double, right: Double): Double = left + right
}

abstract class SubNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Long

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Double

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Double

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Double
}

class SubNodeImpl(language: BridjeLanguage) : SubNode(language) {
    override fun executeLL(left: Long, right: Long): Long = left - right
    override fun executeLD(left: Long, right: Double): Double = left - right
    override fun executeDL(left: Double, right: Long): Double = left - right
    override fun executeDD(left: Double, right: Double): Double = left - right
}

abstract class MulNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Long

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Double

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Double

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Double
}

class MulNodeImpl(language: BridjeLanguage) : MulNode(language) {
    override fun executeLL(left: Long, right: Long): Long = left * right
    override fun executeLD(left: Long, right: Double): Double = left * right
    override fun executeDL(left: Double, right: Long): Double = left * right
    override fun executeDD(left: Double, right: Double): Double = left * right
}

abstract class DivNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Long

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Double

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Double

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Double
}

class DivNodeImpl(language: BridjeLanguage) : DivNode(language) {
    override fun executeLL(left: Long, right: Long): Long {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    override fun executeLD(left: Long, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }

    override fun executeDL(left: Double, right: Long): Double {
        if (right == 0L) throw ArithmeticException("Division by zero")
        return left / right
    }

    override fun executeDD(left: Double, right: Double): Double {
        if (right == 0.0) throw ArithmeticException("Division by zero")
        return left / right
    }
}

// Comparison operators

abstract class EqNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Boolean

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Boolean

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Boolean

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Boolean

    @Specialization
    protected abstract fun executeBB(left: Boolean, right: Boolean): Boolean

    @Specialization
    protected abstract fun executeGeneric(left: Any?, right: Any?): Boolean
}

class EqNodeImpl(language: BridjeLanguage) : EqNode(language) {
    override fun executeLL(left: Long, right: Long): Boolean = left == right
    override fun executeLD(left: Long, right: Double): Boolean = left.toDouble() == right
    override fun executeDL(left: Double, right: Long): Boolean = left == right.toDouble()
    override fun executeDD(left: Double, right: Double): Boolean = left == right
    override fun executeBB(left: Boolean, right: Boolean): Boolean = left == right
    override fun executeGeneric(left: Any?, right: Any?): Boolean = left == right
}

abstract class NeqNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Boolean

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Boolean

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Boolean

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Boolean

    @Specialization
    protected abstract fun executeBB(left: Boolean, right: Boolean): Boolean

    @Specialization
    protected abstract fun executeGeneric(left: Any?, right: Any?): Boolean
}

class NeqNodeImpl(language: BridjeLanguage) : NeqNode(language) {
    override fun executeLL(left: Long, right: Long): Boolean = left != right
    override fun executeLD(left: Long, right: Double): Boolean = left.toDouble() != right
    override fun executeDL(left: Double, right: Long): Boolean = left != right.toDouble()
    override fun executeDD(left: Double, right: Double): Boolean = left != right
    override fun executeBB(left: Boolean, right: Boolean): Boolean = left != right
    override fun executeGeneric(left: Any?, right: Any?): Boolean = left != right
}

abstract class LtNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Boolean

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Boolean

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Boolean

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Boolean
}

class LtNodeImpl(language: BridjeLanguage) : LtNode(language) {
    override fun executeLL(left: Long, right: Long): Boolean = left < right
    override fun executeLD(left: Long, right: Double): Boolean = left < right
    override fun executeDL(left: Double, right: Long): Boolean = left < right
    override fun executeDD(left: Double, right: Double): Boolean = left < right
}

abstract class GtNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Boolean

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Boolean

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Boolean

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Boolean
}

class GtNodeImpl(language: BridjeLanguage) : GtNode(language) {
    override fun executeLL(left: Long, right: Long): Boolean = left > right
    override fun executeLD(left: Long, right: Double): Boolean = left > right
    override fun executeDL(left: Double, right: Long): Boolean = left > right
    override fun executeDD(left: Double, right: Double): Boolean = left > right
}

abstract class LteNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Boolean

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Boolean

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Boolean

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Boolean
}

class LteNodeImpl(language: BridjeLanguage) : LteNode(language) {
    override fun executeLL(left: Long, right: Long): Boolean = left <= right
    override fun executeLD(left: Long, right: Double): Boolean = left <= right
    override fun executeDL(left: Double, right: Long): Boolean = left <= right
    override fun executeDD(left: Double, right: Double): Boolean = left <= right
}

abstract class GteNode(language: BridjeLanguage) : BuiltinBinaryOperatorNode(language) {
    @Specialization
    protected abstract fun executeLL(left: Long, right: Long): Boolean

    @Specialization
    protected abstract fun executeLD(left: Long, right: Double): Boolean

    @Specialization
    protected abstract fun executeDL(left: Double, right: Long): Boolean

    @Specialization
    protected abstract fun executeDD(left: Double, right: Double): Boolean
}

class GteNodeImpl(language: BridjeLanguage) : GteNode(language) {
    override fun executeLL(left: Long, right: Long): Boolean = left >= right
    override fun executeLD(left: Long, right: Double): Boolean = left >= right
    override fun executeDL(left: Double, right: Long): Boolean = left >= right
    override fun executeDD(left: Double, right: Double): Boolean = left >= right
}
