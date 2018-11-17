@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

data class Env(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv): Env = Env(nses + (newNsEnv.ns to newNsEnv))
}

abstract class AGlobalVar internal constructor(val sym: QSymbol, val value: Any?) {
    abstract val type: Type
}

internal class GlobalVar(sym: QSymbol, override val type: Type, value: Any?) : AGlobalVar(sym, value)

internal class ConstructorVar internal constructor(val constructor: DataTypeConstructor, value: Any?) : AGlobalVar(constructor.sym, value) {
    override val type: Type = Type(if (constructor.paramTypes != null) FnType(constructor.paramTypes, constructor.dataType.monoType) else constructor.dataType.monoType)
}

data class DataType internal constructor(val sym: QSymbol, val typeVars: List<TypeVarType>?, val constructors: List<Symbol>) {
    val monoType: MonoType

    init {
        val dataTypeType = DataTypeType(this)
        monoType = if (typeVars == null) dataTypeType else AppliedType(dataTypeType, typeVars)
    }

    override fun toString() = sym.toString()
}

data class DataTypeConstructor internal constructor(val sym: QSymbol, val dataType: DataType, val paramTypes: List<MonoType>?)

data class NSEnv(val ns: Symbol,
                 val refers: Map<Symbol, QSymbol> = emptyMap(),
                 val aliases: Map<Symbol, Symbol> = emptyMap(),
                 val dataTypes: Map<Symbol, DataType> = emptyMap(),
                 val vars: Map<Symbol, AGlobalVar> = emptyMap()) {

    operator fun plus(newGlobalVar: AGlobalVar): NSEnv = copy(vars = vars + (newGlobalVar.sym.name to newGlobalVar))
    operator fun plus(newDataType: DataType): NSEnv = copy(dataTypes = dataTypes + (newDataType.sym.name to newDataType))

    val deps: Set<Symbol> by lazy {
        aliases.values.toSet() + refers.values.map { it.ns }
    }
}

fun resolve(env: Env, nsEnv: NSEnv, sym: Symbol): AGlobalVar? =
    nsEnv.vars[sym]
        ?: nsEnv.refers[sym]?.let { refer -> env.nses[refer.ns]?.vars?.get(refer.name) }

fun resolve(env: Env, nsEnv: NSEnv, sym: QSymbol): AGlobalVar? =
    env.nses[(nsEnv.aliases[sym.ns] ?: sym.ns)]?.let { it.vars[sym.name] }


