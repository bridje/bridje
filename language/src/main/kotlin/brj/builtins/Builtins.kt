package brj.builtins

import brj.BridjeLanguage
import brj.GlobalVar
import brj.runtime.BridjeFunction

object Builtins {
    fun createBuiltinFunctions(language: BridjeLanguage): Map<String, GlobalVar> {
        return mapOf(
            "add" to createBinaryOperator(language, "add", BinaryOperator.ADD),
            "sub" to createBinaryOperator(language, "sub", BinaryOperator.SUB),
            "mul" to createBinaryOperator(language, "mul", BinaryOperator.MUL),
            "div" to createBinaryOperator(language, "div", BinaryOperator.DIV),
            "eq" to createBinaryOperator(language, "eq", BinaryOperator.EQ),
            "neq" to createBinaryOperator(language, "neq", BinaryOperator.NEQ),
            "lt" to createBinaryOperator(language, "lt", BinaryOperator.LT),
            "gt" to createBinaryOperator(language, "gt", BinaryOperator.GT),
            "lte" to createBinaryOperator(language, "lte", BinaryOperator.LTE),
            "gte" to createBinaryOperator(language, "gte", BinaryOperator.GTE)
        )
    }

    private fun createBinaryOperator(
        language: BridjeLanguage,
        name: String,
        operator: BinaryOperator
    ): GlobalVar {
        val node = BuiltinBinaryOperatorNode(language, operator)
        val function = BridjeFunction(node.callTarget)
        return GlobalVar(name, function)
    }
}
