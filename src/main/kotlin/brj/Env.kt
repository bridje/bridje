@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

import brj.Types.MonoType
import brj.Types.MonoType.FnType
import brj.Types.Typing

data class Env(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv): Env = Env(nses + (newNsEnv.ns to newNsEnv))
}

open class GlobalVar internal constructor(val sym: Symbol, val typing: Typing, val value: Any?)

data class DataType(val sym: QSymbol, val typeVars: List<MonoType.TypeVarType>?, val constructors: List<Symbol>)

class DataTypeConstructor internal constructor(sym: Symbol, val dataType: DataType, val paramTypes: List<MonoType>?, value: Any?): GlobalVar(sym, Typing(constructorType(dataType, paramTypes)), value)  {
    companion object {
        fun constructorType(dataType: DataType, paramTypes: List<MonoType>?): MonoType {
            val dataTypeType = MonoType.DataType(dataType.sym)
            val appliedType = dataType.typeVars?.let { MonoType.AppliedType(dataTypeType, it) } ?: dataTypeType

            return if (paramTypes != null) FnType(paramTypes, appliedType) else appliedType
        }
    }
}

data class NSEnv(val ns: Symbol,
                 val refers: Map<Symbol, QSymbol> = emptyMap(),
                 val aliases: Map<Symbol, Symbol> = emptyMap(),
                 val dataTypes: Map<Symbol, DataType> = emptyMap(),
                 val vars: Map<Symbol, GlobalVar> = emptyMap()) {

    operator fun plus(newGlobalVar: GlobalVar): NSEnv = copy(vars = vars + (newGlobalVar.sym to newGlobalVar))
    operator fun plus(newDataType: DataType): NSEnv = copy(dataTypes = dataTypes + (newDataType.sym.name to newDataType))

    val deps: Set<Symbol> by lazy {
        aliases.values.toSet() + refers.values.map { it.ns }
    }
}

fun resolve(env: Env, nsEnv: NSEnv, sym: Symbol): GlobalVar? =
    nsEnv.vars[sym]
        ?: nsEnv.refers[sym]?.let { refer -> env.nses[refer.ns]?.vars?.get(refer.name) }

fun resolve(env: Env, nsEnv: NSEnv, sym: QSymbol): GlobalVar? =
    env.nses[(nsEnv.aliases[sym.ns] ?: sym.ns)]?.let { it.vars[sym.name] }


