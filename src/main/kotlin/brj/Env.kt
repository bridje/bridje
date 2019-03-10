@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

abstract class GlobalVar internal constructor() {
    abstract val sym: QSymbol
    abstract val type: Type
    abstract val value: Any?
}

internal data class DefVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()
internal data class DefMacroVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()

data class RecordKey internal constructor(val sym: QSymbol, val typeVars: List<TypeVarType>, val type: MonoType) {
    override fun toString() = sym.toString()
}

data class RecordKeyVar internal constructor(val recordKey: RecordKey, override var value: Any) : GlobalVar() {
    override val sym = recordKey.sym
    override val type: Type = RecordType.accessorType(recordKey)
}

data class VariantKey internal constructor(val sym: QSymbol, val typeVars: List<TypeVarType>, val paramTypes: List<MonoType>) {
    override fun toString() = sym.toString()
}

data class VariantKeyVar internal constructor(val variantKey: VariantKey, override var value: Any) : GlobalVar() {
    override val sym = variantKey.sym
    override val type: Type = VariantType.constructorType(variantKey)
}

data class JavaImport internal constructor(val clazz: Class<*>, val sym: QSymbol, val type: Type)

class JavaImportVar(javaImport: JavaImport, override val value: Any) : GlobalVar() {
    override val sym = javaImport.sym
    override val type = javaImport.type
}

data class TypeAlias(val sym: QSymbol, val typeVars: List<TypeVarType>, val type: Type?)

data class NSEnv(val ns: Symbol,
                 val refers: Map<Symbol, QSymbol> = emptyMap(),
                 val aliases: Map<Symbol, Symbol> = emptyMap(),
                 val javaImports: Map<QSymbol, JavaImport> = emptyMap(),
                 val typeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                 val vars: Map<Ident, GlobalVar> = emptyMap()) {

    operator fun plus(javaImportVar: JavaImportVar): NSEnv = copy(vars = vars + (javaImportVar.sym to javaImportVar))
    operator fun plus(globalVar: GlobalVar): NSEnv = copy(vars = vars + (globalVar.sym.base to globalVar))
    operator fun plus(alias: TypeAlias) = copy(typeAliases = typeAliases + (alias.sym.base to alias))
}


class Env(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv) = Env(nses + (newNsEnv.ns to newNsEnv))
}

internal fun resolve(env: Env, nsEnv: NSEnv, sym: Ident): GlobalVar? =
    nsEnv.vars[sym]
        ?: when (sym) {
            is Symbol -> nsEnv.refers[sym]?.let { qsym -> env.nses.getValue(qsym.ns).vars.getValue(qsym.base) }
            is QSymbol ->
                (env.nses[(nsEnv.aliases[sym.ns] ?: sym.ns)] ?: TODO("can't find NS"))
                    .vars.getValue(sym.base)
        }

