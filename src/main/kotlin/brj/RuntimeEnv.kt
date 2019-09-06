@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj

import brj.types.*
import com.oracle.truffle.api.TruffleLanguage
import java.math.BigDecimal
import java.math.BigInteger

sealed class Alias {
    abstract val ns: Symbol
}

data class BridjeAlias(override val ns: Symbol) : Alias()
data class JavaAlias(override val ns: Symbol, val clazz: Class<*>) : Alias()

internal abstract class GlobalVar internal constructor() {
    abstract val sym: QSymbol
    abstract val type: Type
    abstract val value: Any?
}

internal data class DefVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()
internal data class EffectVar(override val sym: QSymbol, override val type: Type, val hasDefault: Boolean, override var value: Any?) : GlobalVar()
internal data class DefMacroVar(private val truffleEnv: TruffleLanguage.Env, override val sym: QSymbol, override val type: Type, val formsNS: NSEnv, override var value: Any?) : GlobalVar() {
    private fun fromVariant(obj: VariantObject): Form {
        fun fromVariantList(arg: Any): List<Form> {
            return (arg as List<*>).map { fromVariant(BridjeTypesGen.expectVariantObject(truffleEnv.asGuestValue(it))) }
        }

        val arg = obj.dynamicObject[0].let { arg -> if (truffleEnv.isHostObject(arg)) truffleEnv.asHostObject(arg) else arg }

        return when (obj.variantKey.sym.base.baseStr) {
            "BooleanForm" -> BooleanForm(null, arg as Boolean)
            "StringForm" -> StringForm(null, arg as String)
            "IntForm" -> IntForm(null, arg as Long)
            "FloatForm" -> FloatForm(null, arg as Double)
            "BigIntForm" -> BigIntForm(null, arg as BigInteger)
            "BigFloatForm" -> BigFloatForm(null, arg as BigDecimal)
            "ListForm" -> ListForm(null, fromVariantList(arg))
            "VectorForm" -> VectorForm(null, fromVariantList(arg))
            "SetForm" -> SetForm(null, fromVariantList(arg))
            "RecordForm" -> RecordForm(null, fromVariantList(arg))
            "SymbolForm" -> SymbolForm(null, arg as Symbol)
            "QSymbolForm" -> QSymbolForm(null, arg as QSymbol)
            "QuotedSymbolForm" -> QuotedSymbolForm(null, arg as Symbol)
            "QuotedQSymbolForm" -> QuotedQSymbolForm(null, arg as QSymbol)
            else -> TODO()
        }
    }

    fun evalMacro(argForms: List<Form>): Form {
        fun toVariant(form: Form): Any {
            val arg = when (form.arg) {
                is List<*> -> form.arg.map { toVariant(it as Form) }
                is Form -> toVariant(form)
                else -> form.arg
            }

            return (formsNS.vars[form.qsym.base]!!.value as BridjeFunction).callTarget.call(arg)
        }

        val variantArgs = argForms.map(::toVariant)

        val paramTypes = (type.monoType as FnType).paramTypes
        val isVarargs = paramTypes.last() is VectorType
        val fixedArgCount = if (isVarargs) paramTypes.size - 1 else paramTypes.size

        val args = variantArgs.take(fixedArgCount) + listOfNotNull(if (isVarargs) variantArgs.drop(fixedArgCount) else null)

        return fromVariant(
            (value as BridjeFunction).callTarget
                .call(*(args.toTypedArray()))
                as VariantObject)
    }
}

internal data class RecordKey internal constructor(val sym: QSymbol, val typeVars: List<TypeVarType>, val type: MonoType) {
    override fun toString() = sym.toString()
}

internal data class RecordKeyVar internal constructor(val recordKey: RecordKey, override var value: Any) : GlobalVar() {
    override val sym = recordKey.sym
    override val type: Type = RecordType.accessorType(recordKey)
}

internal data class VariantKey internal constructor(val sym: QSymbol, val typeVars: List<TypeVarType>, val paramTypes: List<MonoType>) {
    override fun toString() = sym.toString()
}

internal data class VariantKeyVar internal constructor(val variantKey: VariantKey, override var value: Any) : GlobalVar() {
    override val sym = variantKey.sym
    override val type: Type = VariantType.constructorType(variantKey)
}

internal data class JavaImport internal constructor(val qsym: QSymbol, val clazz: Class<*>, val name: String, val type: Type)

internal class JavaImportVar(javaImport: JavaImport, override val value: Any) : GlobalVar() {
    override val sym = javaImport.qsym
    override val type = javaImport.type
}

internal class PolyVar(override val sym: QSymbol, val polyTypeVar: TypeVarType, override val type: Type) : GlobalVar() {
    override val value: Any? = null

    override fun toString() = sym.toString()
}

internal class PolyVarImpl(val polyVar: PolyVar, val implType: MonoType, val value: Any)

internal sealed class TypeAlias(open val sym: QSymbol, open val typeVars: List<TypeVarType>, open val type: MonoType?)
internal class TypeAlias_(override val sym: QSymbol, override val typeVars: List<TypeVarType>, override var type: MonoType?) : TypeAlias(sym, typeVars, type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeAlias) return false
        if (sym != other.sym) return false
        return true
    }

    override fun hashCode(): Int {
        return sym.hashCode()
    }
}

internal data class NSEnv(val ns: Symbol,
                          val referVars: Map<Symbol, GlobalVar> = emptyMap(),
                          val referTypeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                          val aliases: Map<Symbol, NSEnv> = emptyMap(),
                          val typeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                          val vars: Map<Symbol, GlobalVar> = emptyMap(),
                          val polyVarImpls: Map<QSymbol, Map<MonoType, PolyVarImpl>> = emptyMap()) {

    operator fun plus(globalVar: GlobalVar): NSEnv = copy(vars = vars + (globalVar.sym.base to globalVar))
    operator fun plus(alias: TypeAlias) = copy(typeAliases = typeAliases + (alias.sym.base to alias))
    operator fun plus(impl: PolyVarImpl): NSEnv {
        val sym = impl.polyVar.sym
        return copy(polyVarImpls = polyVarImpls +
            (sym to (polyVarImpls[sym] ?: emptyMap()) + (impl.implType to impl)))
    }
}

internal class RuntimeEnv(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv) = RuntimeEnv(nses + (newNsEnv.ns to newNsEnv))
}

internal fun NSEnv.resolveTypeAlias(ident: Ident): TypeAlias? =
    typeAliases[ident]
        ?: when (ident) {
            is Symbol -> referTypeAliases[ident]
            is QSymbol -> (aliases[ident.ns] ?: TODO("can't find NS")).typeAliases[ident.base]
        }
