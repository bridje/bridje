package brj

import brj.NSEnv.GlobalVar
import java.math.BigDecimal
import java.math.BigInteger

sealed class ValueExpr

data class BooleanExpr(val boolean: Boolean) : ValueExpr()
data class StringExpr(val string: String) : ValueExpr()
data class IntExpr(val int: Long) : ValueExpr()
data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
data class FloatExpr(val float: Double) : ValueExpr()
data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()

data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()
data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()

data class CallExpr(val f: ValueExpr, val args: List<ValueExpr>) : ValueExpr()

data class FnExpr(val fnName: Symbol? = null, val params: List<LocalVar>, val expr: ValueExpr) : ValueExpr()
data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()
data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

data class LetBinding(val localVar: LocalVar, val expr: ValueExpr)
data class LetExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()

data class CaseClause(val kw: QKeyword, val bindings: List<LocalVar>?, val bodyExpr: ValueExpr)
data class CaseExpr(val expr: ValueExpr, val clauses: List<CaseClause>, val defaultExpr: ValueExpr?) : ValueExpr()

data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()
data class GlobalVarExpr(val globalVar: GlobalVar) : ValueExpr()
