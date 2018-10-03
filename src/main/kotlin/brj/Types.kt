package brj

import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import brj.Types.MonoType.*
import brj.Types.TypeEq.Companion.unifyEqs
import brj.Types.TypeException.ArityError
import brj.Types.TypeException.UnificationError
import brj.Types.Typing.Companion.combine
import java.util.*

object Types {

    data class TypeMapping(val mapping: Map<TypeVarType, MonoType> = emptyMap()) {
        fun applyMapping(mapping: TypeMapping) =
            TypeMapping(
                this.mapping.mapValues { e -> e.value.applyMapping(mapping) }
                    .plus(mapping.mapping))

        fun map(tv: TypeVarType) = mapping.getOrDefault(tv, tv)
    }

    data class MonoEnv(val env: Map<LocalVar, MonoType> = emptyMap()) {
        fun applyMapping(mapping: TypeMapping): MonoEnv = MonoEnv(env.mapValues { e -> e.value.applyMapping(mapping) })
    }

    sealed class MonoType {
        open fun applyMapping(mapping: TypeMapping): MonoType = this
        open fun unifyEq(other: MonoType): List<TypeEq> =
            if (this.javaClass == other.javaClass) emptyList() else throw UnificationError(this, other)

        protected inline fun <reified T : MonoType> cast(t: MonoType): T =
            t as? T ?: throw UnificationError(this, t)

        object BoolType : MonoType() {
            override fun toString(): String = "Bool"
        }

        object StringType : MonoType() {
            override fun toString(): String = "Str"
        }

        object IntType : MonoType() {
            override fun toString(): String = "Int"
        }

        object BigIntType : MonoType() {
            override fun toString(): String = "BigInt"
        }

        object FloatType : MonoType() {
            override fun toString(): String = "Float"
        }

        object BigFloatType : MonoType() {
            override fun toString(): String = "BigFloat"
        }

        class TypeVarType : MonoType() {
            override fun applyMapping(mapping: TypeMapping): MonoType = mapping.map(this)

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
            override fun applyMapping(mapping: TypeMapping): MonoType = VectorType(elType = elType.applyMapping(mapping))
            override fun unifyEq(other: MonoType): List<TypeEq> = listOf(TypeEq(elType, cast<VectorType>(other).elType))
            override fun toString(): String = "[$elType]"
        }

        data class SetType(val elType: MonoType) : MonoType() {
            override fun applyMapping(mapping: TypeMapping): MonoType = SetType(elType = elType.applyMapping(mapping))
            override fun unifyEq(other: MonoType): List<TypeEq> = listOf(TypeEq(elType, cast<SetType>(other).elType))
            override fun toString(): String = "#{$elType}"
        }

        data class FnType(val paramTypes: List<MonoType>, val returnType: MonoType) : MonoType() {
            override fun applyMapping(mapping: TypeMapping): MonoType =
                FnType(paramTypes.map { it.applyMapping(mapping) }, returnType.applyMapping(mapping))

            override fun unifyEq(other: MonoType): List<TypeEq> {
                val otherFnType = cast<FnType>(other)
                if (paramTypes.size != otherFnType.paramTypes.size) throw UnificationError(this, other)

                return paramTypes.zip(otherFnType.paramTypes, ::TypeEq)
                    .plus(TypeEq(returnType, otherFnType.returnType))
            }

            override fun toString(): String = "(Fn [${paramTypes.joinToString(separator = " ")}] $returnType)"
        }
    }

    sealed class TypeException : Exception() {
        data class UnificationError(val t1: MonoType, val t2: MonoType) : TypeException()
        data class ExpectedFunction(val expr: ValueExpr, val type: MonoType) : TypeException()
        data class ArityError(val fnType: FnType, val argExprs: List<ValueExpr>) : TypeException()
    }

    data class TypeEq(val t1: MonoType, val t2: MonoType) {
        companion object {
            fun unifyEqs(eqs_: List<TypeEq>): TypeMapping {
                var eqs = eqs_
                var mapping = TypeMapping()

                while (eqs.isNotEmpty()) {
                    val eq = eqs.first()
                    eqs = eqs.drop(1)

                    val (t1, t2) = if (eq.t2 is TypeVarType) Pair(eq.t2, eq.t1) else Pair(eq.t1, eq.t2)

                    if (t1 == t2) {
                        continue
                    }

                    if (t1 is TypeVarType) {
                        val newMapping = TypeMapping(mapOf(Pair(t1, t2)))
                        mapping = mapping.applyMapping(newMapping)
                        eqs = eqs.map { TypeEq(it.t1.applyMapping(newMapping), it.t2.applyMapping(newMapping)) }
                        continue
                    }

                    eqs = t1.unifyEq(t2).plus(eqs)

                }

                return mapping
            }
        }
    }

    data class Typing(val returnType: MonoType, val monoEnv: MonoEnv = MonoEnv(), val typeEqs: List<TypeEq> = emptyList()) {
        companion object {
            fun combine(returnType: MonoType, typings: List<Typing>, extraEqs: List<TypeEq> = emptyList(), extraMonoEnvs: List<MonoEnv> = emptyList()): Typing {
                val monoEnvs = typings.map { it.monoEnv }.plus(extraMonoEnvs)

                val lvTvs = monoEnvs
                    .flatMapTo(HashSet()) { it.env.keys }
                    .associateBy({ it }, { TypeVarType() })

                val mapping = unifyEqs(monoEnvs
                    .flatMap { env -> env.env.mapTo(LinkedList()) { e -> TypeEq(lvTvs[e.key]!!, e.value) } }
                    .plus(extraEqs))

                return Typing(returnType.applyMapping(mapping), MonoEnv(lvTvs.mapValues { e -> mapping.map(e.value) }))
            }
        }
    }

    private fun collExprTyping(mkCollType: (MonoType) -> MonoType, exprs: List<ValueExpr>): Typing {
        val typings = exprs.map(::valueExprTyping)
        val returnType = TypeVarType()

        return combine(mkCollType(returnType), typings, typings.map { t -> Types.TypeEq(t.returnType, returnType) })
    }

    private fun ifExprTyping(expr: IfExpr): Typing {
        val predTyping = valueExprTyping(expr.predExpr)
        val thenTyping = valueExprTyping(expr.thenExpr)
        val elseTyping = valueExprTyping(expr.elseExpr)

        val returnType = TypeVarType()

        return combine(
            returnType,
            listOf(predTyping, thenTyping, elseTyping),
            listOf(
                TypeEq(BoolType, predTyping.returnType),
                TypeEq(returnType, thenTyping.returnType),
                TypeEq(returnType, elseTyping.returnType)))
    }

    private fun letExprTyping(expr: LetExpr): Typing {
        val bindingPairs = expr.bindings.map { Pair(it.localVar, valueExprTyping(it.expr)) }
        val exprTyping = valueExprTyping(expr.expr)

        return combine(exprTyping.returnType, bindingPairs.map(Pair<*, Typing>::second).plus(exprTyping), extraMonoEnvs = listOf(MonoEnv()))
    }

    private fun fnExprTyping(expr: FnExpr): Typing {
        val typings = expr.bodyExprs.map(this::valueExprTyping)
        val typing = combine(typings.last().returnType, typings)

        return Typing(
            FnType(
                expr.params.map { typing.monoEnv.env.getOrDefault(it, TypeVarType()) },
                typing.returnType),
            MonoEnv(env = typing.monoEnv.env.minus(expr.params)))
    }

    private fun callExprTyping(expr: CallExpr): Typing {
        val fnExpr = expr.f
        val argExprs = expr.args

        val fnExprTyping = valueExprTyping(fnExpr)
        val fnExprType =
            fnExprTyping.returnType as? FnType
                ?: throw TypeException.ExpectedFunction(fnExpr, fnExprTyping.returnType)

        if (fnExprType.paramTypes.size != argExprs.size) throw ArityError(fnExprType, argExprs)

        val argTypings = argExprs.map(this::valueExprTyping)

        return combine(
            fnExprType.returnType,
            argTypings.plus(fnExprTyping),
            fnExprType.paramTypes.zip(argTypings.map(Typing::returnType), ::TypeEq))
    }

    private fun localVarTyping(lv: LocalVar): Typing {
        val tv = TypeVarType()
        return Typing(tv, MonoEnv(mapOf(Pair(lv, tv))))
    }

    fun valueExprTyping(expr: ValueExpr): Typing =
        when (expr) {
            is BooleanExpr -> Typing(BoolType)
            is StringExpr -> Typing(StringType)
            is IntExpr -> Typing(IntType)
            is BigIntExpr -> Typing(BigIntType)
            is FloatExpr -> Typing(FloatType)
            is BigFloatExpr -> Typing(BigFloatType)

            is VectorExpr -> collExprTyping(::VectorType, expr.exprs)
            is SetExpr -> collExprTyping(::SetType, expr.exprs)

            is FnExpr -> fnExprTyping(expr)
            is CallExpr -> callExprTyping(expr)

            is IfExpr -> ifExprTyping(expr)
            is LetExpr -> letExprTyping(expr)

            is LocalVarExpr -> localVarTyping(expr.localVar)
        }
}