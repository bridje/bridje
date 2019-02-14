@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

abstract class GlobalVar internal constructor() {
    abstract val sym: QSymbol
    abstract val type: Type
    abstract val value: Any?
}


internal data class DefVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()


data class RecordKey internal constructor(val sym: QSymbol, val typeVars: List<TypeVarType>?, val type: MonoType) {
    override fun toString() = sym.toString()
}

data class RecordKeyVar internal constructor(val recordKey: RecordKey, override var value: Any?) : GlobalVar() {
    override val sym = recordKey.sym

    override val type: Type by lazy {
        val keys = setOf(recordKey)
        Type(FnType(listOf(RecordType(keys, keys, TypeVarType())), recordKey.type), emptySet())
    }
}

data class VariantKey internal constructor(val sym: QSymbol, val typeVars: List<TypeVarType>?, val paramTypes: List<MonoType>?) {
    override fun toString() = sym.toString()
}

data class VariantKeyVar internal constructor(val variantKey: VariantKey, override var value: Any?) : GlobalVar() {
    override val sym = variantKey.sym
    override val type: Type
        get() {
            val variantType = VariantType(setOf(variantKey), null, TypeVarType())

            return Type(if (variantKey.paramTypes != null)
                FnType(variantKey.paramTypes, variantType)
            else
                variantType, emptySet())
        }
}

data class JavaImport internal constructor(val clazz: Class<*>, val sym: QSymbol, val type: Type)

internal class JavaImportVar(javaImport: JavaImport, override val value: Any? = null) : GlobalVar() {
    override val sym = javaImport.sym
    override val type = javaImport.type
}

data class TypeAlias(val sym: QSymbol, val typeVars: List<TypeVarType>?, val type: Type)

data class NSEnv(val ns: Symbol,
                 val refers: Map<Symbol, QSymbol> = emptyMap(),
                 val aliases: Map<Symbol, Symbol> = emptyMap(),
                 val javaImports: Map<QSymbol, JavaImport> = emptyMap(),
                 val typeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                 val vars: Map<Symbol, GlobalVar> = emptyMap()) {

    operator fun plus(globalVar: GlobalVar): NSEnv = copy(vars = vars + (globalVar.sym.base to globalVar))
    operator fun plus(alias: TypeAlias) = copy(typeAliases = typeAliases + (alias.sym.base to alias))

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

