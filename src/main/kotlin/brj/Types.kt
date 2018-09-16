package brj

import brj.Expr.LocalVar
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import brj.Types.MonoType.*
import brj.Types.TypeEq.Companion.unifyEqs
import brj.Types.Typing.Companion.combine
import java.util.*

object Types {

    data class TypeMapping(val mapping: Map<TypeVarType, MonoType>) {
        fun applyMapping(mapping: TypeMapping) = TypeMapping(this.mapping.mapValues { e -> e.value.applyMapping(mapping) })
        fun map(tv: TypeVarType) = mapping.getOrDefault(tv, tv)
    }

    data class MonoEnv(val env: Map<LocalVar, MonoType> = emptyMap()) {
        fun applyMapping(mapping: TypeMapping): MonoEnv = MonoEnv(env.mapValues { e -> e.value.applyMapping(mapping) })
    }

    sealed class MonoType {
        open fun applyMapping(mapping: TypeMapping): MonoType = this

        object BoolType : MonoType()
        object StringType : MonoType()
        object IntType : MonoType()
        object BigIntType : MonoType()
        object FloatType : MonoType()
        object BigFloatType : MonoType()

        class TypeVarType : MonoType() {
            override fun applyMapping(mapping: TypeMapping): MonoType = mapping.map(this)

            override fun equals(other: Any?): Boolean {
                return this === other
            }

            override fun hashCode(): Int {
                return System.identityHashCode(this)
            }
        }

        data class VectorType(val elType: MonoType) : MonoType() {
            override fun applyMapping(mapping: TypeMapping): MonoType = copy(elType = elType.applyMapping(mapping))
        }

        data class SetType(val elType: MonoType) : MonoType() {
            override fun applyMapping(mapping: TypeMapping): MonoType = copy(elType = elType.applyMapping(mapping))
        }
    }

    data class TypeEq(val t1: MonoType, val t2: MonoType) {
        companion object {
            fun unifyEqs(eqs: List<TypeEq>): TypeMapping {
                TODO()
            }
        }
    }

    data class Typing(val returnType: MonoType, val monoEnv: MonoEnv = MonoEnv(), val typeEqs: List<TypeEq> = emptyList()) {
        companion object {
            fun combine(returnType: MonoType, typings: List<Typing>, extraEqs: List<TypeEq> = emptyList()): Typing {
                val monoEnvs = typings.map { it.monoEnv }

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

            is CallExpr -> TODO()
        }
}