@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

import brj.Symbol.Companion.mkSym
import brj.types.*

sealed class Alias {
    abstract val ns: Symbol
}

data class BridjeAlias(override val ns: Symbol) : Alias()
data class JavaAlias(override val ns: Symbol, val clazz: Class<*>) : Alias()

abstract class GlobalVar internal constructor() {
    abstract val sym: QSymbol
    abstract val type: Type
    abstract val value: Any?
}

data class DefVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()
data class EffectVar(override val sym: QSymbol, override val type: Type, val hasDefault: Boolean, override var value: Any?) : GlobalVar()
data class DefMacroVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()

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

data class JavaImport internal constructor(val qsym: QSymbol, val clazz: Class<*>, val name: String, val type: Type)

class JavaImportVar(val javaImport: JavaImport, override val value: Any) : GlobalVar() {
    override val sym = javaImport.qsym
    override val type = javaImport.type
}

sealed class TypeAlias(open val sym: QSymbol, open val typeVars: List<TypeVarType>, open val type: MonoType?)
internal class TypeAlias_(override val sym: QSymbol, override val typeVars: List<TypeVarType>, override var type: MonoType?) : TypeAlias(sym, typeVars, type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TypeAlias_

        if (sym != other.sym) return false

        return true
    }

    override fun hashCode(): Int {
        return sym.hashCode()
    }
}

data class NSEnv(val ns: Symbol,
                 val refers: Map<Symbol, QSymbol> = emptyMap(),
                 val aliases: Map<Symbol, Alias> = emptyMap(),
                 val typeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                 val vars: Map<Symbol, GlobalVar> = emptyMap()) {

    operator fun plus(globalVar: GlobalVar): NSEnv = copy(vars = vars + (globalVar.sym.base to globalVar))
    operator fun plus(alias: TypeAlias) = copy(typeAliases = typeAliases + (alias.sym.base to alias))
}

class RuntimeEnv(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv) = RuntimeEnv(nses + (newNsEnv.ns to newNsEnv))
}

private val CORE_NS = mkSym("brj.core")

private fun resolveNS(ns: Symbol, env: RuntimeEnv, nsEnv: NSEnv): NSEnv? =
    env.nses[(nsEnv.aliases[ns]?.ns ?: ns)]
        ?: (if (!ns.baseStr.contains('.')) env.nses[mkSym("brj.$ns")] else null)

internal fun resolve(env: RuntimeEnv, nsEnv: NSEnv, sym: Ident): GlobalVar? =
    nsEnv.vars[sym]
        ?: when (sym) {
            is Symbol ->
                nsEnv.refers[sym]?.let { qsym -> env.nses[qsym.ns]?.vars?.get(qsym.base) }
                    ?: resolveNS(CORE_NS, env, nsEnv)!!.vars[sym]
            is QSymbol ->
                (resolveNS(sym.ns, env, nsEnv) ?: TODO("can't find NS"))
                    .vars[sym.base]
        }

internal fun resolveTypeAlias(env: RuntimeEnv, nsEnv: NSEnv, sym: Ident): TypeAlias? =
    nsEnv.typeAliases[sym]
        ?: when (sym) {
            is Symbol -> nsEnv.refers[sym]?.let { qsym -> env.nses.getValue(qsym.ns).typeAliases.getValue(qsym.base) }
            is QSymbol ->
                (env.nses[(nsEnv.aliases[sym.ns]?.ns ?: sym.ns)] ?: TODO("can't find NS"))
                    .typeAliases.getValue(sym.base)
        }