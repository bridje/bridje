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