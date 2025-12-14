package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

enum class BinaryOperator {
    ADD, SUB, MUL, DIV,
    EQ, NEQ, LT, GT, LTE, GTE
}

class BuiltinBinaryOperatorNode(
    language: BridjeLanguage,
    private val operator: BinaryOperator
) : RootNode(language) {

    override fun execute(frame: VirtualFrame): Any? {
        val args = frame.arguments
        if (args.size != 2) {
            throw IllegalArgumentException("Binary operator ${operator.name.lowercase()} requires exactly 2 arguments, got ${args.size}")
        }

        val left = args[0]
        val right = args[1]

        return when (operator) {
            BinaryOperator.ADD -> performAdd(left, right)
            BinaryOperator.SUB -> performSub(left, right)
            BinaryOperator.MUL -> performMul(left, right)
            BinaryOperator.DIV -> performDiv(left, right)
            BinaryOperator.EQ -> performEq(left, right)
            BinaryOperator.NEQ -> !performEq(left, right)
            BinaryOperator.LT -> performLt(left, right)
            BinaryOperator.GT -> performGt(left, right)
            BinaryOperator.LTE -> performLte(left, right)
            BinaryOperator.GTE -> performGte(left, right)
        }
    }

    private fun performAdd(left: Any?, right: Any?): Any {
        return when {
            left is Long && right is Long -> left + right
            left is Long && right is Double -> left + right
            left is Double && right is Long -> left + right
            left is Double && right is Double -> left + right
            else -> throw UnsupportedOperationException("Cannot add $left and $right")
        }
    }

    private fun performSub(left: Any?, right: Any?): Any {
        return when {
            left is Long && right is Long -> left - right
            left is Long && right is Double -> left - right
            left is Double && right is Long -> left - right
            left is Double && right is Double -> left - right
            else -> throw UnsupportedOperationException("Cannot subtract $left and $right")
        }
    }

    private fun performMul(left: Any?, right: Any?): Any {
        return when {
            left is Long && right is Long -> left * right
            left is Long && right is Double -> left * right
            left is Double && right is Long -> left * right
            left is Double && right is Double -> left * right
            else -> throw UnsupportedOperationException("Cannot multiply $left and $right")
        }
    }

    private fun performDiv(left: Any?, right: Any?): Any {
        return when {
            left is Long && right is Long -> {
                if (right == 0L) throw ArithmeticException("Division by zero")
                left / right
            }
            left is Long && right is Double -> {
                if (right == 0.0) throw ArithmeticException("Division by zero")
                left / right
            }
            left is Double && right is Long -> {
                if (right == 0L) throw ArithmeticException("Division by zero")
                left / right
            }
            left is Double && right is Double -> {
                if (right == 0.0) throw ArithmeticException("Division by zero")
                left / right
            }
            else -> throw UnsupportedOperationException("Cannot divide $left and $right")
        }
    }

    private fun performEq(left: Any?, right: Any?): Boolean {
        return when {
            left is Long && right is Long -> left == right
            left is Long && right is Double -> left.toDouble() == right
            left is Double && right is Long -> left == right.toDouble()
            left is Double && right is Double -> left == right
            left is Boolean && right is Boolean -> left == right
            else -> left == right
        }
    }

    private fun performLt(left: Any?, right: Any?): Boolean {
        return when {
            left is Long && right is Long -> left < right
            left is Long && right is Double -> left < right
            left is Double && right is Long -> left < right
            left is Double && right is Double -> left < right
            else -> throw UnsupportedOperationException("Cannot compare $left and $right")
        }
    }

    private fun performGt(left: Any?, right: Any?): Boolean {
        return when {
            left is Long && right is Long -> left > right
            left is Long && right is Double -> left > right
            left is Double && right is Long -> left > right
            left is Double && right is Double -> left > right
            else -> throw UnsupportedOperationException("Cannot compare $left and $right")
        }
    }

    private fun performLte(left: Any?, right: Any?): Boolean {
        return when {
            left is Long && right is Long -> left <= right
            left is Long && right is Double -> left <= right
            left is Double && right is Long -> left <= right
            left is Double && right is Double -> left <= right
            else -> throw UnsupportedOperationException("Cannot compare $left and $right")
        }
    }

    private fun performGte(left: Any?, right: Any?): Boolean {
        return when {
            left is Long && right is Long -> left >= right
            left is Long && right is Double -> left >= right
            left is Double && right is Long -> left >= right
            left is Double && right is Double -> left >= right
            else -> throw UnsupportedOperationException("Cannot compare $left and $right")
        }
    }
}
