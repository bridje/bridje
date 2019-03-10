package brj

import brj.Symbol.Companion.mkSym
import brj.TypeException.ArityError
import brj.TypeException.UnificationError
import brj.analyser.*

internal val STR = mkSym("Str")
internal val BOOL = mkSym("Bool")
internal val INT = mkSym("Int")
internal val FLOAT = mkSym("Float")
internal val BIG_INT = mkSym("BigInt")
internal val BIG_FLOAT = mkSym("BigFloat")
internal val FN_TYPE = mkSym("Fn")
internal val VARIANT_TYPE = mkSym("+")

internal data class Mapping(val typeMapping: Map<TypeVarType, MonoType> = emptyMap()) {
    fun applyMapping(mapping: Mapping) =
        Mapping(this.typeMapping
            .mapValues { e -> e.value.applyMapping(mapping) }
            .plus(mapping.typeMapping))
}

typealias MonoEnv = Map<LocalVar, MonoType>

internal class Instantiator {
    private val tvMapping = mutableMapOf<TypeVarType, TypeVarType>()

    fun instantiate(type: MonoType): MonoType {
        return when (type) {
            is TypeVarType -> tvMapping.getOrPut(type, ::TypeVarType)
            else -> type.fmap(this::instantiate)
        }
    }
}

data class Type(val monoType: MonoType, val effects: Set<QSymbol> = emptySet()) {
    override fun toString() = monoType.toString()

    // TODO check matching
    fun matches(expectedType: Type): Boolean = true
}

sealed class MonoType {
    internal open val javaType: Class<*>? = Object::class.java

    internal open fun unifyEq(other: MonoType): List<TypeEq> =
        if (this.javaClass == other.javaClass) emptyList() else throw UnificationError(this, other)

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

class TypeVarType : MonoType() {
    override fun applyMapping(mapping: Mapping): MonoType = mapping.typeMapping.getOrDefault(this, this)

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    override fun toString(): String {
        return "TV(${hashCode()})"
    }
}

data class VectorType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> = listOf(TypeEq(elType, ensure<VectorType>(other).elType))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = VectorType(f(elType))

    override fun toString(): String = "[$elType]"
}

data class SetType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> = listOf(TypeEq(elType, ensure<SetType>(other).elType))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = SetType(f(elType))

    override fun toString(): String = "#{$elType}"
}

data class FnType(val paramTypes: List<MonoType>, val returnType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> {
        val otherFnType = ensure<FnType>(other)
        if (paramTypes.size != otherFnType.paramTypes.size) throw UnificationError(this, other)

        return paramTypes.zip(otherFnType.paramTypes, ::TypeEq)
            .plus(TypeEq(returnType, otherFnType.returnType))
    }

    override fun fmap(f: (MonoType) -> MonoType): MonoType = FnType(paramTypes.map(f), f(returnType))

    override fun toString(): String = "(Fn ${paramTypes.joinToString(separator = " ")} $returnType)"
}

private fun <L, R> Iterable<L>?.safeZip(other: Iterable<R>?): Iterable<Pair<L, R>> =
    if (this != null && other != null) this.zip(other) else emptyList()

private fun <E> Set<E>.ifNotEmpty() = this.takeIf { it.isNotEmpty() }

private fun <E> intersectionTop(left: Set<E>?, right: Set<E>?) =
    if (left != null && right != null) left.intersect(right) else left ?: right

data class KeyType<K>(val key: K, val typeParams: List<MonoType>) {
    internal fun fmap(f: (MonoType) -> MonoType) = KeyType(key, typeParams.map(f))

    override fun toString() = if (typeParams.isNotEmpty()) "($key ${typeParams.joinToString(" ")})" else key.toString()
}

data class RecordType(val hasKeys: Set<RecordKey>,
                      val mustHaveKeys: Set<RecordKey>?,
                      val keyTypes: Map<RecordKey, KeyType<RecordKey>>,
                      val typeVar: TypeVarType) : MonoType() {

    companion object {
        internal fun accessorType(recordKey: RecordKey): Type {
            val recordType = RecordType(setOf(recordKey), setOf(recordKey), mapOf(recordKey to KeyType(recordKey, recordKey.typeVars)), TypeVarType())

            return Type(FnType(listOf(recordType), recordKey.type), emptySet())
        }
    }

    // TODO is this right?
    private fun minus(other: RecordType, newTypeVar: TypeVarType) =
        RecordType(
            hasKeys - other.hasKeys,
            if (mustHaveKeys != null && other.mustHaveKeys != null) (mustHaveKeys - other.mustHaveKeys) else mustHaveKeys
                ?: other.mustHaveKeys,
            emptyMap(),
            newTypeVar)

    override fun fmap(f: (MonoType) -> MonoType) =
        RecordType(hasKeys, mustHaveKeys, keyTypes.mapValues { it.value.fmap(f) }, typeVar)

    override fun unifyEq(other: MonoType): List<TypeEq> {
        val otherRecord = ensure<RecordType>(other)

        @Suppress("NAME_SHADOWING", "NestedLambdaShadowedImplicitParameter")
        fun missingKeys(hasKeys: Set<RecordKey>, mustHaveKeys: Set<RecordKey>?) =
            mustHaveKeys.orEmpty().minus(hasKeys).ifNotEmpty()

        missingKeys(hasKeys, otherRecord.mustHaveKeys)?.let { TODO() }
        missingKeys(otherRecord.hasKeys, mustHaveKeys)?.let { TODO() }

        val keyTypeEqs = (keyTypes.keys + otherRecord.keyTypes.keys)
            .flatMap { variantKey -> keyTypes[variantKey]?.typeParams.safeZip(otherRecord.keyTypes[variantKey]?.typeParams) }

        val newTypeVar = TypeVarType()

        val newTypeVarEqs = listOf(
            typeVar to otherRecord.minus(this, newTypeVar),
            otherRecord.typeVar to this.minus(otherRecord, newTypeVar))

        return keyTypeEqs + newTypeVarEqs
    }

    override fun applyMapping(mapping: Mapping) =
        ((mapping.typeMapping[typeVar] as? RecordType)?.let { other ->
            RecordType(
                hasKeys.union(other.hasKeys),
                intersectionTop(mustHaveKeys, other.mustHaveKeys),
                // TODO not convinced about this either
                keyTypes.mapValues { it.value.fmap { it.applyMapping(mapping) } },
                other.typeVar)
        } ?: this)
            .fmap { it.applyMapping(mapping) }

    override fun toString() = "{${keyTypes.values.joinToString(" ")}}"
}

data class VariantType(val possibleKeys: Set<VariantKey> = emptySet(),
                       val allowedKeys: Set<VariantKey>? = null,
                       val keyTypes: Map<VariantKey, KeyType<VariantKey>>,
                       val typeVar: TypeVarType) : MonoType() {

    companion object {
        internal fun constructorType(variantKey: VariantKey): Type {
            val variantType = VariantType(setOf(variantKey), null, mapOf(variantKey to KeyType(variantKey, variantKey.typeVars)), TypeVarType())

            return Type(if (variantKey.paramTypes.isEmpty()) variantType else FnType(variantKey.paramTypes, variantType), emptySet())
        }
    }

    override fun fmap(f: (MonoType) -> MonoType): MonoType =
        VariantType(possibleKeys, allowedKeys, keyTypes.mapValues { it.value.fmap(f) }, typeVar)

    // TODO is this right?
    private fun minus(other: VariantType, newTypeVar: TypeVarType) =
        VariantType(
            possibleKeys - other.possibleKeys,
            (allowedKeys.orEmpty() - other.allowedKeys.orEmpty()).ifNotEmpty(),
            keyTypes,
            newTypeVar)

    override fun unifyEq(other: MonoType): List<TypeEq> {
        val otherVariant = ensure<VariantType>(other)

        @Suppress("NAME_SHADOWING", "NestedLambdaShadowedImplicitParameter")
        fun disallowedKeys(possibleKeys: Set<VariantKey>, allowedKeys: Set<VariantKey>?) =
            if (allowedKeys != null) (possibleKeys - allowedKeys).ifNotEmpty() else null

        disallowedKeys(possibleKeys, otherVariant.allowedKeys)?.let { TODO() }
        disallowedKeys(otherVariant.possibleKeys, allowedKeys)?.let { TODO() }

        // TODO I reckon we should create a whole load more TypeEqs here, from type params through to new type vars
        val keyTypeEqs = (keyTypes.keys + otherVariant.keyTypes.keys)
            .flatMap { variantKey -> keyTypes[variantKey]?.typeParams.safeZip(otherVariant.keyTypes[variantKey]?.typeParams) }

        val newTypeVar = TypeVarType()

        val newTypeVarEqs = listOf(
            typeVar to otherVariant.minus(this, newTypeVar),
            otherVariant.typeVar to this.minus(otherVariant, newTypeVar))

        return keyTypeEqs + newTypeVarEqs
    }

    override fun applyMapping(mapping: Mapping): MonoType =
        ((mapping.typeMapping[typeVar] as? VariantType)?.let { other ->
            VariantType(
                possibleKeys.union(other.possibleKeys),
                intersectionTop(allowedKeys, other.allowedKeys),
                // TODO not convinced about this either
                other.keyTypes,
                other.typeVar)
        } ?: this)
            .fmap { it.applyMapping(mapping) }

    override fun toString() = "(+ ${keyTypes.values.joinToString(" ")})"
}

sealed class TypeException : Exception() {
    data class UnificationError(val t1: MonoType, val t2: MonoType) : TypeException()
    data class ExpectedFunction(val expr: ValueExpr, val type: MonoType) : TypeException()
    data class ArityError(val fnType: FnType, val argExprs: List<ValueExpr>) : TypeException()
}

typealias TypeEq = Pair<MonoType, MonoType>

private fun unifyEqs(eqs_: List<TypeEq>): Mapping {
    var eqs = eqs_
    var mapping = Mapping()

    while (eqs.isNotEmpty()) {
        val eq = eqs.first()
        eqs = eqs.drop(1)

        val (t1, t2) = if (eq.second is TypeVarType) (eq.second to eq.first) else (eq.first to eq.second)

        if (t1 == t2) {
            continue
        }

        if (t1 is TypeVarType) {
            val newMapping = Mapping(mapOf(t1 to t2))
            mapping = mapping.applyMapping(newMapping)
            eqs = eqs.map { TypeEq(it.first.applyMapping(newMapping), it.second.applyMapping(newMapping)) }
            continue
        }

        eqs = t1.unifyEq(t2).plus(eqs)

    }

    return mapping
}

private data class Typing(val monoType: MonoType, val monoEnv: MonoEnv = emptyMap(), val effects: Set<QSymbol> = emptySet())

private fun combine(
    returnType: MonoType,
    typings: Iterable<Typing> = emptyList(),
    extraEqs: Iterable<TypeEq> = emptyList(),
    extraLVs: Iterable<Pair<LocalVar, MonoType>> = emptyList()
): Typing {

    val lvTvs: MutableMap<LocalVar, TypeVarType> = mutableMapOf()

    val mapping = unifyEqs(
        typings
            .flatMapTo(extraLVs.toMutableList()) { it.monoEnv.toList() }
            .mapTo(extraEqs.toMutableList()) { e -> TypeEq(lvTvs.getOrPut(e.first, ::TypeVarType), e.second) })

    return Typing(
        returnType.applyMapping(mapping),
        lvTvs.mapValues { e -> mapping.typeMapping.getOrDefault(e.value, e.value) },
        typings.flatMapTo(HashSet(), Typing::effects))
}

private fun collExprTyping(mkCollType: (MonoType) -> MonoType, exprs: List<ValueExpr>): Typing {
    val typings = exprs.map(::valueExprTyping)
    val returnType = TypeVarType()

    return combine(mkCollType(returnType), typings, extraEqs = typings.map { it.monoType to returnType })
}

private fun recordExprTyping(expr: RecordExpr): Typing {
    val typings = expr.entries.map { it.recordKey to valueExprTyping(it.expr) }

    val instantiator = Instantiator()

    val recordType = RecordType(
        expr.entries.mapTo(mutableSetOf(), RecordEntry::recordKey),
        null,
        expr.entries.associate { it.recordKey to KeyType(it.recordKey, it.recordKey.typeVars).fmap(instantiator::instantiate) },
        TypeVarType())

    return combine(
        recordType,
        typings.map { it.second },
        extraEqs = typings.map { instantiator.instantiate(it.first.type) to it.second.monoType })
}

private fun ifExprTyping(expr: IfExpr): Typing {
    val predExprTyping = valueExprTyping(expr.predExpr)
    val thenExprTyping = valueExprTyping(expr.thenExpr)
    val elseExprTyping = valueExprTyping(expr.elseExpr)

    val returnType = TypeVarType()

    return combine(returnType,
        typings = listOf(predExprTyping, thenExprTyping, elseExprTyping),
        extraEqs = listOf(
            BoolType to predExprTyping.monoType,
            returnType to thenExprTyping.monoType,
            returnType to elseExprTyping.monoType))
}

private fun letExprTyping(expr: LetExpr): Typing {
    val bindingTypings = expr.bindings.map { it.localVar to valueExprTyping(it.expr) }
    val exprTyping = valueExprTyping(expr.expr)

    return combine(exprTyping.monoType,
        typings = bindingTypings.map { it.second } + exprTyping,
        extraLVs = bindingTypings.map { it.first to it.second.monoType })
}

private fun doExprTyping(expr: DoExpr): Typing {
    val exprTypings = expr.exprs.map(::valueExprTyping)
    val exprTyping = valueExprTyping(expr.expr)

    return combine(exprTyping.monoType, exprTypings + exprTyping)
}

private fun loopExprTyping(expr: LoopExpr): Typing {
    val bindingTypings = expr.bindings.map { it to valueExprTyping(it.expr) }

    val bodyTyping = valueExprTyping(expr.expr)

    return combine(bodyTyping.monoType,
        typings = bindingTypings.map { it.second } + bodyTyping,
        extraLVs = bindingTypings.map { it.first.localVar to it.second.monoType })
}

private fun recurExprTyping(expr: RecurExpr): Typing {
    val exprTypings = expr.exprs.map { valueExprTyping(it.second) }

    return combine(TypeVarType(), exprTypings,
        extraLVs = expr.exprs.map { it.first }.zip(exprTypings.map(Typing::monoType)))
}

private fun fnExprTyping(expr: FnExpr): Typing {
    val params = expr.params.map { it to TypeVarType() }
    val exprTyping = valueExprTyping(expr.expr)

    return combine(FnType(params.map { it.second }, exprTyping.monoType), listOf(exprTyping), extraLVs = params)
}

private fun callExprTyping(expr: CallExpr): Typing {
    val fnExpr = expr.f
    val argExprs = expr.args

    val fnExprTyping = valueExprTyping(fnExpr)

    val fnExprType = fnExprTyping.monoType.let {
        it as? FnType ?: throw TypeException.ExpectedFunction(fnExpr, it)
    }

    if (fnExprType.paramTypes.size != argExprs.size) throw ArityError(fnExprType, argExprs)

    val argTypings = argExprs.map(::valueExprTyping)

    return combine(fnExprType.returnType,
        typings = argTypings + fnExprTyping,
        extraEqs = fnExprType.paramTypes.zip(argTypings.map(Typing::monoType)))
}

private fun localVarTyping(lv: LocalVar): Typing {
    val typeVar = TypeVarType()
    return combine(typeVar, extraLVs = listOf(lv to typeVar))
}

private fun caseExprTyping(expr: CaseExpr): Typing {
    val returnType = TypeVarType()

    val exprTyping = valueExprTyping(expr.expr)

    val clauseTypings = expr.clauses.map { clause ->
        if (clause.variantKey.paramTypes.size != clause.bindings.size) TODO()
        clause to valueExprTyping(clause.bodyExpr)
    }

    val defaultTyping = expr.defaultExpr?.let { valueExprTyping(it) }

    val instantiator = Instantiator()

    val variantKeys = clauseTypings.map { it.first.variantKey }.toSet()
    val variantType = instantiator.instantiate(VariantType(emptySet(), variantKeys, variantKeys.associateWith { KeyType(it, it.typeVars) }, TypeVarType()))

    return combine(returnType,
        typings = (clauseTypings.map { it.second } + exprTyping + defaultTyping).filterNotNull(),
        extraEqs = (
            clauseTypings.map { returnType to it.second.monoType }
                + (exprTyping.monoType to variantType)
                + defaultTyping?.let { returnType to it.monoType }).filterNotNull(),
        extraLVs = clauseTypings.flatMap { (clause, _) -> clause.bindings.zip(clause.variantKey.paramTypes.map(instantiator::instantiate)) }
    )
}

private fun valueExprTyping(expr: ValueExpr): Typing =
    when (expr) {
        is BooleanExpr -> Typing(BoolType)
        is StringExpr -> Typing(StringType)
        is IntExpr -> Typing(IntType)
        is BigIntExpr -> Typing(BigIntType)
        is FloatExpr -> Typing(FloatType)
        is BigFloatExpr -> Typing(BigFloatType)

        is VectorExpr -> collExprTyping(::VectorType, expr.exprs)
        is SetExpr -> collExprTyping(::SetType, expr.exprs)

        is RecordExpr -> recordExprTyping(expr)

        is FnExpr -> fnExprTyping(expr)
        is CallExpr -> callExprTyping(expr)

        is IfExpr -> ifExprTyping(expr)
        is LetExpr -> letExprTyping(expr)
        is DoExpr -> doExprTyping(expr)

        is LoopExpr -> loopExprTyping(expr)
        is RecurExpr -> recurExprTyping(expr)

        is LocalVarExpr -> localVarTyping(expr.localVar)
        is GlobalVarExpr -> expr.globalVar.type.let { Typing(Instantiator().instantiate(it.monoType), effects = it.effects) }

        is CaseExpr -> caseExprTyping(expr)
    }

fun valueExprType(expr: ValueExpr) = valueExprTyping(expr).let { Type(it.monoType, it.effects) }
