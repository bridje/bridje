package brj.builtins

import brj.BridjeLanguage
import brj.GlobalVar
import brj.runtime.BridjeFunction
import brj.runtime.Symbol
import brj.runtime.sym
import brj.types.*
import com.oracle.truffle.api.nodes.RootNode

object Builtins {
    private val symbolType = TagType(TagRef("brj.core".sym, "Symbol".sym), emptyList())
    private val varType = TagType(TagRef("brj.core".sym, "Var".sym), emptyList())

    fun createBuiltinFunctions(language: BridjeLanguage): Map<Symbol, GlobalVar> {
        fun numericBinOp(name: String, node: RootNode): GlobalVar {
            val t = TypeVar()
            return createBuiltinFunction(name, node, FnType(listOf(t, t), t))
        }

        fun comparisonOp(name: String, node: RootNode): GlobalVar {
            val t = TypeVar()
            return createBuiltinFunction(name, node, FnType(listOf(t, t), BoolType))
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
            createBuiltinFunction("println", PrintlnNode(language), FnType(listOf(TypeVar()), NilType)),
            // TODO: gensym is variadic (0 or 1 args) — FnType can't represent optional arity yet
            createBuiltinFunction("gensym", GensymNode(language), TypeVar()),
            createBuiltinFunction("nth", NthNodeGen.create(language, ReadArgumentNode(0), ReadArgumentNode(1)),
                run { val el = TypeVar(); FnType(listOf(VectorType(el), IntType), el) }),
            // meta yields whatever record its argument carries; nothing is known of its keys here.
            createBuiltinFunction("meta", MetaNode(language), FnType(listOf(TypeVar()), TypeVar())),
            createBuiltinFunction("withMeta", WithMetaNode(language),
                run { val t = TypeVar(); FnType(listOf(t, RecordType(emptySet())), t) }),
            createBuiltinFunction("throw", ThrowNode(language), FnType(listOf(TypeVar()), BottomType)),
            createBuiltinFunction("not", NotNode(language), FnType(listOf(BoolType), BoolType)),
            comparisonOp("isSame", SameNode(language)),
            createBuiltinFunction("itr", ItrNode(language),
                run { val a = TypeVar(); FnType(listOf(IterableType(a)), IteratorType(a)) }),
            createBuiltinFunction("itrHasNext", ItrHasNextNode(language),
                run { val a = TypeVar(); FnType(listOf(IteratorType(a)), BoolType) }),
            createBuiltinFunction("itrNext", ItrNextNode(language),
                run { val a = TypeVar(); FnType(listOf(IteratorType(a)), a) }),
            createBuiltinFunction("allNses", AllNsesNode(language), FnType(emptyList(), VectorType(symbolType))),
            createBuiltinFunction("nsVars", NsVarsNode(language), FnType(listOf(symbolType), VectorType(varType))),
        ).associateBy { it.name }
    }

    private fun createBuiltinFunction(name: String, node: RootNode, type: Type) =
        GlobalVar("brj.core".sym, Symbol.intern(name), BridjeFunction(node.callTarget), scheme = Scheme(type))
}
