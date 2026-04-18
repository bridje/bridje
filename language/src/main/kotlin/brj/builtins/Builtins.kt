package brj.builtins

import brj.BridjeLanguage
import brj.GlobalVar
import brj.runtime.BridjeFunction
import brj.runtime.Symbol
import brj.types.*
import brj.types.Type
import com.oracle.truffle.api.nodes.RootNode

object Builtins {
    fun createBuiltinFunctions(language: BridjeLanguage): Map<Symbol, GlobalVar> {
        fun numericBinOp(name: String, node: RootNode): GlobalVar {
            val t = freshType()
            return createBuiltinFunction(name, node, FnType(listOf(t, t), t).notNull())
        }

        fun comparisonOp(name: String, node: RootNode): GlobalVar {
            val t = freshType()
            return createBuiltinFunction(name, node, FnType(listOf(t, t), BoolType.notNull()).notNull())
        }

        return listOf(
            numericBinOp("add", AddNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            numericBinOp("sub", SubNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            numericBinOp("mul", MulNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            numericBinOp("div", DivNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            comparisonOp("eq", EqNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            comparisonOp("neq", NeqNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            comparisonOp("lt", LtNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            comparisonOp("gt", GtNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            comparisonOp("lte", LteNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            comparisonOp("gte", GteNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1))),
            createBuiltinFunction("println", PrintlnNode(language),
                FnType(listOf(freshType()), nullType()).notNull()),
            // TODO: gensym is variadic (0 or 1 args) — FnType can't represent optional arity yet
            createBuiltinFunction("gensym", GensymNode(language), freshType()),
            createBuiltinFunction("nth", NthNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1)),
                run { val el = freshType(); FnType(listOf(VectorType(el).notNull(), IntType.notNull()), el).notNull() }),
            createBuiltinFunction("meta", MetaNode(language),
                FnType(listOf(freshType()), RecordType.notNull()).notNull()),
            createBuiltinFunction("withMeta", WithMetaNode(language),
                run { val t = freshType(); FnType(listOf(t, RecordType.notNull()), t).notNull() }),
            createBuiltinFunction("varMeta", VarMetaNode(language),
                FnType(listOf(FormType.notNull()), RecordType.notNull()).notNull()),
            createBuiltinFunction("throw", ThrowNode(language),
                FnType(listOf(freshType()), nothingType()).notNull()),
            createBuiltinFunction("not", NotNode(language),
                FnType(listOf(BoolType.notNull()), BoolType.notNull()).notNull()),
            comparisonOp("same?", SameNode(language)),
            createBuiltinFunction("itr", ItrNode(language),
                run { val a = freshType(); FnType(listOf(IterableType(a).notNull()), IteratorType(a).notNull()).notNull() }),
            createBuiltinFunction("itrHasNext?", ItrHasNextNode(language),
                run { val a = freshType(); FnType(listOf(IteratorType(a).notNull()), BoolType.notNull()).notNull() }),
            createBuiltinFunction("itrNext", ItrNextNode(language),
                run { val a = freshType(); FnType(listOf(IteratorType(a).notNull()), a).notNull() }),
        ).associateBy { it.name }
    }

    private fun createBuiltinFunction(name: String, node: RootNode, type: Type) =
        GlobalVar(Symbol.intern(name), BridjeFunction(node.callTarget), type = type)
}
