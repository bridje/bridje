package brj

import com.oracle.truffle.api.source.SourceSection
import java.math.BigDecimal
import java.math.BigInteger

sealed interface Expr {
    val loc: SourceSection?
    override fun toString(): String
}

class IntExpr(val value: Long, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = value.toString()
}

class DoubleExpr(val value: Double, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = value.toString()
}

class BigIntExpr(val value: BigInteger, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = "${value}N"
}

class BigDecExpr(val value: BigDecimal, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = "${value}M"
}

class StringExpr(val value: String, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = "\"${value.reescape()}\""
}

class VectorExpr(val els: List<Expr>, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = els.joinToString(prefix = "[", separator = " ", postfix = "]")
}

class SetExpr(val els: List<Expr>, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = els.joinToString(prefix = "#{", separator = " ", postfix = "}")
}

class MapExpr(val els: List<Expr>, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = els.joinToString(prefix = "{", separator = " ", postfix = "}")
}

class LocalVarExpr(val localVar: LocalVar, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = localVar.name
}

class LetExpr(
    val localVar: LocalVar,
    val bindingExpr: Expr,
    val bodyExpr: Expr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(let [${localVar.name} $bindingExpr] $bodyExpr)"
}

class FnExpr(
    val fnName: String,
    val params: List<String>,
    val bodyExpr: Expr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(fn ($fnName ${params.joinToString(" ")}) $bodyExpr)"
}

class CallExpr(
    val fnExpr: Expr,
    val argExprs: List<Expr>,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "($fnExpr ${argExprs.joinToString(" ")})"
}

class DoExpr(
    val sideEffects: List<Expr>,
    val result: Expr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String {
        val all = sideEffects + result
        return "(do ${all.joinToString(" ")})"
    }
}

class BoolExpr(val value: Boolean, override val loc: SourceSection? = null) : Expr {
    override fun toString(): String = value.toString()
}

class IfExpr(
    val predExpr: Expr,
    val thenExpr: Expr,
    val elseExpr: Expr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(if $predExpr $thenExpr $elseExpr)"
}

class DefExpr(
    val name: String,
    val expr: Expr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(def $name $expr)"
}

class GlobalVarExpr(
    val globalVar: GlobalVar,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = globalVar.name
}

sealed class TopLevelDoOrExpr

class TopLevelDo(val forms: List<Form>) : TopLevelDoOrExpr()

class TopLevelExpr(val expr: Expr) : TopLevelDoOrExpr()