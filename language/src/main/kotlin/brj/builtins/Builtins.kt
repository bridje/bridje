package brj.builtins

import brj.BridjeLanguage
import brj.GlobalVar
import brj.runtime.BridjeFunction

object Builtins {
    fun createBuiltinFunctions(language: BridjeLanguage): Map<String, GlobalVar> {
        return mapOf(
            "add" to createBuiltinFunction("add", AddNodeGen.create(language)),
            "sub" to createBuiltinFunction("sub", SubNodeGen.create(language)),
            "mul" to createBuiltinFunction("mul", MulNodeGen.create(language)),
            "div" to createBuiltinFunction("div", DivNodeGen.create(language)),
            "eq" to createBuiltinFunction("eq", EqNodeGen.create(language)),
            "neq" to createBuiltinFunction("neq", NeqNodeGen.create(language)),
            "lt" to createBuiltinFunction("lt", LtNodeGen.create(language)),
            "gt" to createBuiltinFunction("gt", GtNodeGen.create(language)),
            "lte" to createBuiltinFunction("lte", LteNodeGen.create(language)),
            "gte" to createBuiltinFunction("gte", GteNodeGen.create(language))
        )
    }

    private fun createBuiltinFunction(
        name: String,
        node: BuiltinBinaryOperatorNode
    ): GlobalVar {
        val function = BridjeFunction(node.callTarget)
        return GlobalVar(name, function)
    }
}
