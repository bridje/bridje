@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

data class Env(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv): Env = Env(nses + (newNsEnv.ns to newNsEnv))
}

class GlobalVar(val sym: LocalIdent, val typing: Types.Typing, val value: Any?)

data class DataType(val sym: QSymbol, val typeVars: List<Types.MonoType.TypeVarType>?, val constructors: List<Keyword>)

data class DataTypeConstructor(val kw: Keyword, val dataType: DataType, val paramTypes: List<Types.MonoType>?, val value: Any?)

data class NSEnv(val ns: Symbol,
                 val refers: Map<LocalIdent, GlobalIdent> = emptyMap(),
                 val aliases: Map<Symbol, Symbol> = emptyMap(),
                 val dataTypes: Map<Symbol, DataType> = emptyMap(),
                 val vars: Map<LocalIdent, GlobalVar> = emptyMap()) {


    operator fun plus(newGlobalVar: GlobalVar): NSEnv = copy(vars = vars + (newGlobalVar.sym to newGlobalVar))
    operator fun plus(newDataType: DataType): NSEnv = copy(dataTypes = dataTypes + (newDataType.sym.name to newDataType))

    val deps: Set<Symbol> by lazy {
        aliases.values.toSet() + refers.values.map { it.ns }
    }
}


