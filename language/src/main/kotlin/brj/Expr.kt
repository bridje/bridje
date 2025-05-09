package brj

import com.oracle.truffle.api.source.SourceSection

sealed interface Expr {
    val loc: SourceSection
    override fun toString(): String
}

class IntExpr(val value: Long, override val loc: SourceSection) : Expr {
    override fun toString(): String = value.toString()
}

class DoubleExpr(val value: Double, override val loc: SourceSection) : Expr {
    override fun toString(): String = value.toString()
}

class StringExpr(val value: String, override val loc: SourceSection) : Expr {
    override fun toString(): String = "\"${value.reescape()}\""
}

class VectorExpr(val els: List<Expr>, override val loc: SourceSection) : Expr {
    override fun toString(): String = els.joinToString(prefix = "[", separator = " ", postfix = "]")
}

class SetExpr(val els: List<Expr>, override val loc: SourceSection) : Expr {
    override fun toString(): String = els.joinToString(prefix = "#{", separator = " ", postfix = "}")
}

class MapExpr(val els: List<Expr>, override val loc: SourceSection) : Expr {
    override fun toString(): String = els.joinToString(prefix = "{", separator = " ", postfix = "}")
}