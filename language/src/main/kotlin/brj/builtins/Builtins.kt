package brj.builtins

import brj.BridjeLanguage
import brj.GlobalVar
import brj.runtime.BridjeFunction

object Builtins {
    fun createBuiltinFunctions(language: BridjeLanguage): Map<String, GlobalVar> =
        listOf(
            createBuiltinFunction("add", AddNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("sub", SubNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("mul", MulNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("div", DivNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("eq", EqNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("neq", NeqNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("lt", LtNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("gt", GtNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("lte", LteNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("gte", GteNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
        ).associateBy { it.name }

    private fun createBuiltinFunction(name: String, opNode: BinaryOpNode) =
        GlobalVar(name, BridjeFunction(opNode.callTarget))
}
