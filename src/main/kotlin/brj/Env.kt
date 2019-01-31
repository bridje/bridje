@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

abstract class GlobalVar internal constructor() {
    abstract val sym: QSymbol
    abstract val type: Type
    abstract val value: Any?
}


internal class DefVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()


data class Attribute internal constructor(val sym: QSymbol, val type: MonoType) {
    override fun toString() = sym.toString()
}

data class AttributeVar internal constructor(val attribute: Attribute, override var value: Any?) : GlobalVar() {
    override val sym = attribute.sym
    override val type: Type get() = TODO("not implemented")
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


internal class ConstructorVar(val constructor: DataTypeConstructor, override var value: Any?) : GlobalVar() {
    override val sym = constructor.sym
    override val type =
        Type(
            if (constructor.paramTypes != null) FnType(constructor.paramTypes, constructor.dataType.monoType)
            else constructor.dataType.monoType,
            emptySet())
}


data class JavaImport internal constructor(val clazz: Class<*>, val sym: QSymbol, val type: Type)

internal class JavaImportVar(javaImport: JavaImport, override val value: Any? = null) : GlobalVar() {
    override val sym = javaImport.sym
    override val type = javaImport.type
}


data class NSEnv(val ns: Symbol,
                 val refers: Map<Symbol, QSymbol> = emptyMap(),
                 val aliases: Map<Symbol, Symbol> = emptyMap(),
                 val javaImports: Map<QSymbol, JavaImport> = emptyMap(),
                 val vars: Map<Symbol, GlobalVar> = emptyMap()) {

    operator fun plus(newGlobalVar: GlobalVar): NSEnv = copy(vars = vars + (newGlobalVar.sym.base to newGlobalVar))

    val deps: Set<Symbol> by lazy {
        aliases.values.toSet() + refers.values.map { it.ns }
    }
}

fun resolve(env: Env, nsEnv: NSEnv, sym: Symbol): GlobalVar? =
    nsEnv.vars[sym]
        ?: nsEnv.refers[sym]?.let { refer -> env.nses[refer.ns]?.vars?.get(refer.base) }

fun resolve(env: Env, nsEnv: NSEnv, sym: QSymbol): GlobalVar? =
    env.nses[(nsEnv.aliases[sym.ns] ?: sym.ns)]?.let { it.vars[sym.base] }

class Env(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv) = Env(nses + (newNsEnv.ns to newNsEnv))
}

