package brj

import brj.TypeException.ArityError
import brj.TypeException.UnificationError
import java.util.*

internal data class TypeMapping(val mapping: Map<TypeVarType, MonoType> = emptyMap()) {
    fun applyMapping(mapping: TypeMapping) =
        TypeMapping(
            this.mapping.mapValues { e -> e.value.applyMapping(mapping) }
                .plus(mapping.mapping))

    fun map(tv: TypeVarType) = mapping.getOrDefault(tv, tv)
}

typealias MonoEnv = MutableMap<LocalVar, MonoType>

internal class Instantiator {
    val mapping: MutableMap<TypeVarType, TypeVarType> = mutableMapOf()

    operator fun invoke(type: MonoType): MonoType {
        return when (type) {
            is TypeVarType -> mapping.getOrPut(type) { TypeVarType() }
            else -> type.fmap(this::invoke)
        }
    }
}

data class Type(val monoType: MonoType) {
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

    internal open fun applyMapping(mapping: TypeMapping): MonoType = fmap { it.applyMapping(mapping) }
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
    override fun applyMapping(mapping: TypeMapping): MonoType = mapping.mapping.getOrDefault(this, this)

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
    override fun fmap(f: (MonoType) -> MonoType): MonoType = VectorType(f(elType))

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

    override fun toString(): String = "(Fn [${paramTypes.joinToString(separator = " ")}] $returnType)"
}

data class DataTypeType(val dataType: DataType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> =
        if (this == other) emptyList() else throw UnificationError(this, other)

    override fun toString(): String = dataType.toString()
}

data class AppliedType(val type: MonoType, val params: List<MonoType>) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> {
        val otherAppliedType = ensure<AppliedType>(other)
        if (this.params.size != otherAppliedType.params.size) throw UnificationError(this, other)

        return params.zip(otherAppliedType.params, ::TypeEq)
            .plus(TypeEq(this.type, otherAppliedType.type))
    }

    override fun fmap(f: (MonoType) -> MonoType): MonoType =
        AppliedType(f(type), params.map(f))

    override fun toString(): String = "($type ${params.joinToString(" ")})"
}

sealed class TypeException : Exception() {
    data class UnificationError(val t1: MonoType, val t2: MonoType) : TypeException()
    data class ExpectedFunction(val expr: ValueExpr, val type: MonoType) : TypeException()
    data class ArityError(val fnType: FnType, val argExprs: List<ValueExpr>) : TypeException()
}

typealias TypeEq = Pair<MonoType, MonoType>

private fun unifyEqs(eqs_: List<TypeEq>): TypeMapping {
    var eqs = eqs_
    var mapping = TypeMapping()

    while (eqs.isNotEmpty()) {
        val eq = eqs.first()
        eqs = eqs.drop(1)

        val (t1, t2) = if (eq.second is TypeVarType) (eq.second to eq.first) else (eq.first to eq.second)

        if (t1 == t2) {
            continue
        }

        if (t1 is TypeVarType) {
            val newMapping = TypeMapping(mapOf(t1 to t2))
            mapping = mapping.applyMapping(newMapping)
            eqs = eqs.map { TypeEq(it.first.applyMapping(newMapping), it.second.applyMapping(newMapping)) }
            continue
        }

        eqs = t1.unifyEq(t2).plus(eqs)

    }

    return mapping
}

internal class TypeChecker(val env: Env) {

    private var monoEnv: MonoEnv = mutableMapOf()

    private fun combine(returnType: MonoType, extraEqs: List<TypeEq> = emptyList(), extraLVs: Iterable<Pair<LocalVar, MonoType>> = emptyList()): MonoType {
        val lvTvs: MutableMap<LocalVar, TypeVarType> = mutableMapOf()

        val mapping = unifyEqs(
            monoEnv.toList()
                .plus(extraLVs)
                .mapTo(LinkedList()) { e -> TypeEq(lvTvs.getOrPut(e.first, ::TypeVarType), e.second) }
                .plus(extraEqs))

        monoEnv = lvTvs.mapValuesTo(mutableMapOf()) { e -> mapping.map(e.value) }

        return returnType.applyMapping(mapping)
    }

    private fun collExprType(mkCollType: (MonoType) -> MonoType, exprs: List<ValueExpr>): MonoType {
        val types = exprs.map(::valueExprType)
        val returnType = TypeVarType()

        return combine(mkCollType(returnType), extraEqs = types.map { TypeEq(it, returnType) })
    }

    private fun ifExprType(expr: IfExpr): MonoType {
        val returnType = TypeVarType()

        return combine(returnType,
            listOf(
                TypeEq(BoolType, valueExprType(expr.predExpr)),
                TypeEq(returnType, valueExprType(expr.thenExpr)),
                TypeEq(returnType, valueExprType(expr.elseExpr))))
    }

    private fun letExprType(expr: LetExpr): MonoType =
        combine(valueExprType(expr.expr),
            extraLVs = expr.bindings.map { it.localVar to valueExprType(it.expr) })

    private fun doExprType(expr: DoExpr): MonoType {
        expr.exprs.map(::valueExprType)

        return combine(valueExprType(expr.expr))
    }

    private fun fnExprType(expr: FnExpr): MonoType =
        combine(FnType(
            expr.params.map { monoEnv.getOrPut(it, ::TypeVarType) },
            valueExprType(expr.expr)))

    private fun callExprType(expr: CallExpr): MonoType {
        val fnExpr = expr.f
        val argExprs = expr.args

        val fnExprType = valueExprType(fnExpr).let { it as? FnType ?: throw TypeException.ExpectedFunction(fnExpr, it) }

        if (fnExprType.paramTypes.size != argExprs.size) throw ArityError(fnExprType, argExprs)

        val argTypes = argExprs.map(this::valueExprType)

        return combine(fnExprType.returnType,
            extraEqs = fnExprType.paramTypes.zip(argTypes))
    }

    private fun localVarType(lv: LocalVar): MonoType {
        return monoEnv.getOrPut(lv, ::TypeVarType)
    }

    private fun caseExprType(expr: CaseExpr): MonoType {
        val returnType = TypeVarType()
        val extraEqs = mutableListOf<TypeEq>()
        val extraLVs = mutableListOf<Pair<LocalVar, MonoType>>()

        val exprType = valueExprType(expr.expr)

        expr.clauses.forEach {clause ->
            val constructor = clause.constructor

            extraEqs += exprType to constructor.dataType.monoType
            if (constructor.paramTypes?.size != clause.bindings?.size) TODO()

            if (constructor.paramTypes != null && clause.bindings != null) {
                extraLVs += clause.bindings.zip(constructor.paramTypes)
            }

            extraEqs += returnType to valueExprType(clause.bodyExpr)
        }

        expr.defaultExpr?.let {
            extraEqs += returnType to valueExprType(it)
        }

        return combine(returnType, extraEqs, extraLVs)
    }

    internal fun valueExprType(expr: ValueExpr): MonoType =
        when (expr) {
            is BooleanExpr -> BoolType
            is StringExpr -> StringType
            is IntExpr -> IntType
            is BigIntExpr -> BigIntType
            is FloatExpr -> FloatType
            is BigFloatExpr -> BigFloatType

            is VectorExpr -> collExprType(::VectorType, expr.exprs)
            is SetExpr -> collExprType(::SetType, expr.exprs)

            is FnExpr -> fnExprType(expr)
            is CallExpr -> callExprType(expr)

            is IfExpr -> ifExprType(expr)
            is LetExpr -> letExprType(expr)
            is DoExpr -> doExprType(expr)

            is LoopExpr -> TODO()
            is RecurExpr -> TODO()

            is LocalVarExpr -> localVarType(expr.localVar)
            is GlobalVarExpr -> Instantiator()(expr.globalVar.type.monoType)

            is CaseExpr -> caseExprType(expr)
        }
}

fun valueExprType(env: Env, expr: ValueExpr) = Type(TypeChecker(env).valueExprType(expr))
