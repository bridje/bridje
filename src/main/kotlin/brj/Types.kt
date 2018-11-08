package brj

import brj.BrjEnv.NSEnv.DataTypeConstructor
import brj.Types.MonoType.*
import brj.Types.TypeEq.Companion.unifyEqs
import brj.Types.TypeException.ArityError
import brj.Types.TypeException.UnificationError
import brj.Types.Typing.Companion.combine
import brj.ValueExpr.*
import java.util.*

object Types {
    data class TypeMapping(val mapping: Map<TypeVarType, MonoType> = emptyMap()) {
        fun applyMapping(mapping: TypeMapping) =
            TypeMapping(
                this.mapping.mapValues { e -> e.value.applyMapping(mapping) }
                    .plus(mapping.mapping))

        fun map(tv: TypeVarType) = mapping.getOrDefault(tv, tv)
    }

    data class MonoEnv(val env: Map<LocalVar, MonoType> = emptyMap()) : Map<LocalVar, MonoType> by env {
        fun applyMapping(mapping: TypeMapping): MonoEnv = MonoEnv(env.mapValues { e -> e.value.applyMapping(mapping) })

        operator fun minus(localVars: Iterable<LocalVar>): MonoEnv = MonoEnv(env.minus(localVars))
    }

    internal class Instantiator {
        val mapping: MutableMap<TypeVarType, TypeVarType> = mutableMapOf()

        operator fun invoke(type: MonoType): MonoType {
            return when (type) {
                is TypeVarType -> mapping.getOrPut(type) { TypeVarType() }
                else -> type.fmap(this::invoke)
            }
        }
    }

    sealed class MonoType {
        open val javaType: Class<*>? = Object::class.java

        open fun unifyEq(other: MonoType): List<TypeEq> =
            if (this.javaClass == other.javaClass) emptyList() else throw UnificationError(this, other)

        protected inline fun <reified T : MonoType> ensure(t: MonoType): T =
            t as? T ?: throw UnificationError(this, t)

        open fun fmap(f: (MonoType) -> MonoType): MonoType = this

        open fun applyMapping(mapping: TypeMapping): MonoType = fmap { it.applyMapping(mapping) }

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

        data class DataType(val sym: NamespacedSymbol) : MonoType() {
            override fun unifyEq(other: MonoType): List<TypeEq> =
                if (this == other) emptyList() else throw UnificationError(this, other)

            override fun toString(): String = sym.toString()
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

                    val (t1, t2) = if (eq.t2 is TypeVarType) (eq.t2 to eq.t1) else (eq.t1 to eq.t2)

                    if (t1 == t2) {
                        continue
                    }

                    if (t1 is TypeVarType) {
                        val newMapping = TypeMapping(mapOf(t1 to t2))
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

    data class Typing(val monoType: MonoType, val monoEnv: MonoEnv = MonoEnv()) {
        // TODO need to check against the expected typing
        fun matches(expectedTyping: Typing): Boolean = true

        companion object {
            fun combine(monoType: MonoType, typings: List<Typing>, extraEqs: List<TypeEq> = emptyList()): Typing {
                val monoEnvs = typings.map { it.monoEnv }

                val lvTvs = monoEnvs
                    .flatMapTo(HashSet()) { it.env.keys }
                    .associate { it to TypeVarType() }

                val mapping = unifyEqs(monoEnvs
                    .flatMap { env -> env.env.mapTo(LinkedList()) { e -> TypeEq(lvTvs[e.key]!!, e.value) } }
                    .plus(extraEqs))

                return Typing(monoType.applyMapping(mapping), MonoEnv(lvTvs.mapValues { e -> mapping.map(e.value) }))
            }
        }
    }

    data class TypeChecker(val brjEnv: BrjEnv, val polyEnv: Map<LocalVar, Typing> = emptyMap()) {

        private fun collExprTyping(mkCollType: (MonoType) -> MonoType, exprs: List<ValueExpr>): Typing {
            val typings = exprs.map(::valueExprTyping)
            val returnType = TypeVarType()

            return combine(mkCollType(returnType), typings, typings.map { t -> Types.TypeEq(t.monoType, returnType) })
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
                    TypeEq(BoolType, predTyping.monoType),
                    TypeEq(returnType, thenTyping.monoType),
                    TypeEq(returnType, elseTyping.monoType)))
        }

        private fun letExprTyping(expr: LetExpr): Typing {
            val bindingPairs = expr.bindings.map { it.localVar to valueExprTyping(it.expr) }
            val exprTyping = valueExprTyping(expr.expr)

            val typing = combine(exprTyping.monoType, bindingPairs.map(Pair<*, Typing>::second).plus(exprTyping))
            return typing.copy(monoEnv = typing.monoEnv - expr.bindings.map(Binding::localVar))
        }

        private fun doExprTyping(expr: DoExpr): Typing {
            val exprTypings = expr.exprs.map(::valueExprTyping)
            val exprTyping = valueExprTyping(expr.expr)

            return combine(exprTyping.monoType, exprTypings.plus(exprTyping))
        }

        private fun fnExprTyping(expr: FnExpr): Typing {
            val typing = valueExprTyping(expr.expr)

            return Typing(
                FnType(
                    expr.params.map { typing.monoEnv.env.getOrDefault(it, TypeVarType()) },
                    typing.monoType),
                typing.monoEnv - expr.params)
        }

        private fun callExprTyping(expr: CallExpr): Typing {
            val fnExpr = expr.f
            val argExprs = expr.args

            val fnExprTyping = valueExprTyping(fnExpr)
            val fnExprType =
                fnExprTyping.monoType as? FnType
                    ?: throw TypeException.ExpectedFunction(fnExpr, fnExprTyping.monoType)

            if (fnExprType.paramTypes.size != argExprs.size) throw ArityError(fnExprType, argExprs)

            val argTypings = argExprs.map(this::valueExprTyping)

            return combine(
                fnExprType.returnType,
                argTypings.plus(fnExprTyping),
                fnExprType.paramTypes.zip(argTypings.map(Typing::monoType), ::TypeEq))
        }

        private fun localVarTyping(lv: LocalVar): Typing {
            val tv = TypeVarType()
            return Typing(tv, MonoEnv(mapOf(lv to tv)))
        }

        private fun constructorExprTyping(constructor: DataTypeConstructor): Typing {
            val dataType = DataType(constructor.dataType.sym)
            val typeVars = constructor.dataType.typeVars

            val appliedDataType = if (typeVars != null) AppliedType(dataType, typeVars) else dataType
            val type = Instantiator()(if (constructor.paramTypes == null) appliedDataType else FnType(constructor.paramTypes, appliedDataType))

            return Typing(type)
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
                is DoExpr -> doExprTyping(expr)

                is LocalVarExpr -> localVarTyping(expr.localVar)
                is GlobalVarExpr -> expr.globalVar.typing

                is ConstructorExpr -> constructorExprTyping(expr.constructor)
            }
    }
}