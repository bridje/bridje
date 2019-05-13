package brj.types

import brj.QSymbol
import brj.RecordKey
import brj.Symbol.Companion.mkSym
import brj.TypeAlias
import brj.VariantKey
import brj.types.TypeException.UnificationError

internal val STR = mkSym("Str")
internal val BOOL = mkSym("Bool")
internal val INT = mkSym("Int")
internal val FLOAT = mkSym("Float")
internal val BIG_INT = mkSym("BigInt")
internal val BIG_FLOAT = mkSym("BigFloat")
internal val SYMBOL = mkSym("Symbol")
internal val QSYMBOL = mkSym("QSymbol")
internal val FN_TYPE = mkSym("Fn")
internal val VARIANT_TYPE = mkSym("+")

data class Type(val monoType: MonoType, val effects: Set<QSymbol> = emptySet()) {
    override fun toString() = if (effects.isEmpty()) monoType.toString() else "(! $monoType #{${effects.joinToString(", ")}})"
}

sealed class MonoType {
    internal open val javaType: Class<*>? = Object::class.java

    internal open fun unifyEq(other: MonoType): Unification =
        if (this.javaClass == other.javaClass) Unification() else throw UnificationError(this, other)

    protected inline fun <reified T : MonoType> ensure(t: MonoType): T =
        t as? T ?: throw UnificationError(this, t)

    open fun fmap(f: (MonoType) -> MonoType): MonoType = this

    internal open fun applyMapping(mapping: Mapping): MonoType = fmap { it.applyMapping(mapping) }
}

object BoolType : MonoType() {
    override val javaType: Class<*>? = Boolean::class.javaPrimitiveType

    override fun toString(): String = "Bool"
}

object StringType : MonoType() {
    override fun toString(): String = "Str"
}

object IntType : MonoType() {
    override val javaType: Class<*>? = Long::class.javaPrimitiveType
    override fun toString(): String = "Int"
}

object BigIntType : MonoType() {
    override fun toString(): String = "BigInt"
}

object FloatType : MonoType() {
    override val javaType: Class<*>? = Double::class.javaPrimitiveType
    override fun toString(): String = "Float"
}

object BigFloatType : MonoType() {
    override fun toString(): String = "BigFloat"
}

object SymbolType : MonoType() {
    override fun toString(): String = "Symbol"
}

object QSymbolType : MonoType() {
    override fun toString(): String = "QSymbol"
}

class TypeVarType : MonoType() {
    override fun applyMapping(mapping: Mapping): MonoType = mapping.typeMapping.getOrDefault(this, this)

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    override fun toString(): String {
        return "tv${hashCode() % 10000}"
    }
}

data class VectorType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType) = Unification(listOf(TypeEq(elType, ensure<VectorType>(other).elType)))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = VectorType(f(elType))

    override fun toString(): String = "[$elType]"
}

data class SetType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType) = Unification(listOf(TypeEq(elType, ensure<SetType>(other).elType)))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = SetType(f(elType))

    override fun toString(): String = "#{$elType}"
}

data class FnType(val paramTypes: List<MonoType>, val returnType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): Unification {
        val otherFnType = ensure<FnType>(other)
        if (paramTypes.size != otherFnType.paramTypes.size) throw UnificationError(this, other)

        return Unification(paramTypes.zip(otherFnType.paramTypes, ::TypeEq)
            .plus(TypeEq(returnType, otherFnType.returnType)))
    }

    override fun fmap(f: (MonoType) -> MonoType): MonoType = FnType(paramTypes.map(f), f(returnType))

    override fun toString(): String = "(Fn ${paramTypes.joinToString(separator = " ")} $returnType)"
}

private fun <L, R> Iterable<L>?.safeZip(other: Iterable<R>?): Iterable<Pair<L, R>> =
    if (this != null && other != null) this.zip(other) else emptyList()

class RowTypeVar(val open: Boolean) {
    override fun toString() = "r${hashCode()}${if (open) "*" else ""}"
}

data class RowKey(val typeParams: List<MonoType>) {
    internal fun fmap(f: (MonoType) -> MonoType) = RowKey(typeParams.map(f))
}

data class RecordType(val hasKeys: Map<RecordKey, RowKey>,
                      val typeVar: RowTypeVar) : MonoType() {

    companion object {
        internal fun accessorType(recordKey: RecordKey): Type {
            val recordType = RecordType(mapOf(recordKey to RowKey(recordKey.typeVars)), RowTypeVar(true))

            return Type(FnType(listOf(recordType), recordKey.type), emptySet())
        }
    }

    override fun unifyEq(other: MonoType): Unification {
        val otherRecord: RecordType = ensure(other)

        val newTypeVar = RowTypeVar(typeVar.open && otherRecord.typeVar.open)

        fun minus(left: RecordType, right: RecordType): Pair<RowTypeVar, Pair<Map<RecordKey, RowKey>, RowTypeVar>> {
            val keyDiff = left.hasKeys - right.hasKeys.keys
            if (!right.typeVar.open && keyDiff.isNotEmpty()) {
                TODO("missing keys: ${keyDiff.keys}")
            }

            return right.typeVar to Pair(keyDiff, newTypeVar)
        }

        return Unification(recordEqs = mapOf(
            minus(otherRecord, this),
            minus(this, otherRecord)))
    }

    override fun fmap(f: (MonoType) -> MonoType) = RecordType(hasKeys.mapValues { it.value.fmap(f) }, typeVar)

    override fun applyMapping(mapping: Mapping) =
        (mapping.recordMapping[typeVar]?.let { (newKeys, newTypeVar) -> RecordType(hasKeys + newKeys, newTypeVar) }
            ?: this)
            .fmap { it.applyMapping(mapping) }

    override fun toString() = "{${hasKeys.keys.joinToString(" ")}}"
}

data class VariantType(val possibleKeys: Map<VariantKey, RowKey>, val typeVar: RowTypeVar) : MonoType() {

    companion object {
        internal fun constructorType(variantKey: VariantKey): Type {
            val variantType = VariantType(mapOf(variantKey to RowKey(variantKey.typeVars)), RowTypeVar(true))

            return Type(if (variantKey.paramTypes.isEmpty()) variantType else FnType(variantKey.paramTypes, variantType), emptySet())
        }
    }

    override fun unifyEq(other: MonoType): Unification {
        val otherVariant = ensure<VariantType>(other)

        val newTypeVar = RowTypeVar(typeVar.open && otherVariant.typeVar.open)

        fun minus(left: VariantType, right: VariantType): Pair<RowTypeVar, Pair<Map<VariantKey, RowKey>, RowTypeVar>> {
            val keyDiff = left.possibleKeys - right.possibleKeys.keys
            if (!right.typeVar.open && keyDiff.isNotEmpty()) {
                TODO("too many keys: ${keyDiff.keys}")
            }

            return right.typeVar to Pair(keyDiff, newTypeVar)
        }

        val typeVarEqs: List<TypeEq> = (this.possibleKeys.keys + otherVariant.possibleKeys.keys).flatMap {
            (possibleKeys[it]?.typeParams ?: emptyList()) zip (otherVariant.possibleKeys[it]?.typeParams ?: emptyList())
        }

        return Unification(
            typeEqs = typeVarEqs,
            variantEqs = mapOf(
                minus(this, otherVariant),
                minus(otherVariant, this)
            ))
    }

    override fun fmap(f: (MonoType) -> MonoType) = VariantType(possibleKeys.mapValues { it.value.fmap(f) }, typeVar)

    override fun applyMapping(mapping: Mapping): MonoType =
        (mapping.variantMapping[typeVar]?.let { (newKeys, newTypeVar) ->
            VariantType(possibleKeys + newKeys, newTypeVar)
        } ?: this)
            .fmap { it.applyMapping(mapping) }

    override fun toString() =
        "(+ " +
            possibleKeys.map { if (it.value.typeParams.isNotEmpty()) "(${it.key} ${it.value.typeParams.joinToString(" ")})" else "${it.key}" }
                .joinToString(" ") +
            ")"
}

data class TypeAliasType(val typeAlias: TypeAlias, val typeParams: List<MonoType>) : MonoType() {
    override fun fmap(f: (MonoType) -> MonoType): MonoType = TypeAliasType(typeAlias, typeParams.map { it.fmap(f) })
    override fun unifyEq(other: MonoType): Unification = Unification(typeEqs = listOf(TypeEq(other, typeAlias.type!!)))

    override fun toString() = if (typeParams.isEmpty()) typeAlias.sym.toString() else "(${typeAlias.sym} ${typeParams.joinToString(" ")})"
}
