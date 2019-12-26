@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj.runtime

import brj.emitter.BridjeFunction
import brj.reader.*
import brj.types.*
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import kotlin.reflect.KClass

internal abstract class GlobalVar {
    abstract val sym: QSymbol
    abstract val type: Type
    abstract val value: Any?
}

internal data class DefVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()

internal data class EffectVar(override val sym: QSymbol, override val type: Type, var defaultImpl: BridjeFunction?, override var value: Any) : GlobalVar()

internal data class DefMacroVar(private val truffleEnv: TruffleLanguage.Env, override val sym: QSymbol, override val type: Type, override val value: BridjeFunction) : GlobalVar() {
    private val paramTypes = (type.monoType as FnType).paramTypes
    private val isVarargs = paramTypes.last() is VectorType
    private val fixedArgCount = if (isVarargs) paramTypes.size - 1 else paramTypes.size

    fun evalMacro(argForms: List<Form>): Form {
        val args = listOf(value.lexObj) + argForms.take(fixedArgCount) + listOfNotNull(if (isVarargs) argForms.drop(fixedArgCount) else null)

        return value.callTarget.call(*(args.toTypedArray())) as Form
    }
}

internal data class RecordKey(val sym: QSymbol, val typeVars: List<TypeVarType>, val type: MonoType) {
    override fun toString() = sym.toString()
}

internal data class RecordKeyVar(val recordKey: RecordKey, override val value: Any) : GlobalVar() {
    override val sym = recordKey.sym
    override val type: Type = RecordType.accessorType(recordKey)
}

internal data class VariantKey(val sym: QSymbol, val typeVars: List<TypeVarType>, val paramTypes: List<MonoType>) {
    override fun toString() = sym.toString()
}

internal data class VariantKeyVar(val variantKey: VariantKey, override val value: Any) : GlobalVar() {
    override val sym = variantKey.sym
    override val type: Type = VariantType.constructorType(variantKey)
}

internal data class JavaImport(val qsym: QSymbol, val clazz: KClass<*>, val name: String, val type: Type)

internal class JavaImportVar(javaImport: JavaImport, override val value: Any) : GlobalVar() {
    override val sym = javaImport.qsym
    override val type = javaImport.type
}

internal data class PolyVar(val polyConstraint: PolyConstraint, val monoType: MonoType, override val value: Any) : GlobalVar() {
    override val sym = polyConstraint.sym
    override val type = Type(monoType)

    override fun toString() = sym.toString()
}

internal data class PolyVarImpl(val polyVar: PolyVar, val primaryImplTypes: List<MonoType>, val secondaryImplTypes: List<MonoType>, val value: Any)

internal sealed class TypeAlias(open val sym: QSymbol, open val typeVars: List<TypeVarType>, open val type: MonoType?)
internal class TypeAlias_(override val sym: QSymbol, override val typeVars: List<TypeVarType> = emptyList(), override var type: MonoType?) : TypeAlias(sym, typeVars, type) {
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
                          val typeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                          val vars: Map<Symbol, GlobalVar> = emptyMap(),
                          val polyVarImpls: Map<QSymbol, List<PolyVarImpl>> = emptyMap()) {

    operator fun plus(globalVar: GlobalVar): NSEnv = copy(vars = vars + (globalVar.sym.base to globalVar))
    operator fun plus(alias: TypeAlias) = copy(typeAliases = typeAliases + (alias.sym.base to alias))
    operator fun plus(impl: PolyVarImpl): NSEnv {
        val sym = impl.polyVar.sym
        return copy(polyVarImpls = polyVarImpls +
            (sym to (polyVarImpls[sym]
                ?: emptyList()).filterNot { it.primaryImplTypes == impl.primaryImplTypes } + impl))
    }
}

private class FormConstructorRootNode(val construct: (Any) -> Any) : RootNode(null) {
    override fun execute(frame: VirtualFrame) = construct(frame.arguments[1])
}

private val FORMS_NSENV = NSEnv(FORM_NS,
    typeAliases = mapOf(FORM to FORM_TYPE_ALIAS),
    vars = META_FORMS.associate {
        it.variantKey.sym.base to VariantKeyVar(it.variantKey, BridjeFunction(FormConstructorRootNode(it::construct)))
    })

internal class RuntimeEnv(val nses: Map<Symbol, NSEnv> = mapOf(FORMS_NSENV.ns to FORMS_NSENV)) {
    operator fun plus(newNsEnv: NSEnv) = RuntimeEnv(nses + (newNsEnv.ns to newNsEnv))
    operator fun plus(newNsEnvs: Iterable<NSEnv>) = RuntimeEnv(nses + newNsEnvs.map { it.ns to it })
}
