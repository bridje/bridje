package brj

import com.oracle.truffle.api.source.SourceSection
import java.util.*

internal sealed class Expr {
    abstract val loc: SourceSection?
}

internal sealed class ValueExpr : Expr()

internal class IntExpr(val int: Int, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is IntExpr && int == other.int)

    override fun hashCode() = Objects.hash(int)
}

internal class BoolExpr(val bool: Boolean, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is BoolExpr && bool == other.bool)

    override fun hashCode() = Objects.hash(bool)
}

internal class StringExpr(val string: String, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is StringExpr && string == other.string)

    override fun hashCode() = Objects.hash(string)

    override fun toString() = "\"$string\""
}

internal class VectorExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is VectorExpr && exprs == other.exprs)

    override fun hashCode() = Objects.hash(exprs)
}


internal class SetExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is SetExpr && exprs == other.exprs)

    override fun hashCode() = Objects.hash(exprs)
}

internal class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is DoExpr && exprs == other.exprs && expr == other.expr)

    override fun hashCode() = Objects.hash(exprs, expr)
}

internal class IfExpr(
    val predExpr: ValueExpr,
    val thenExpr: ValueExpr,
    val elseExpr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other ||
            (other is IfExpr
                && predExpr == other.predExpr
                && thenExpr == other.thenExpr
                && elseExpr == other.elseExpr)

    override fun hashCode() = Objects.hash(predExpr, thenExpr, elseExpr)
}

internal class LetBinding(val binding: LocalVar, val expr: ValueExpr) {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is LetBinding -> false
        else -> binding == other.binding && expr == other.expr
    }

    override fun hashCode() = Objects.hash(binding, expr)
}

internal class LetExpr(
    val bindings: List<LetBinding>,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr()

internal class FnExpr(
    val params: List<LocalVar>,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is FnExpr -> false
        else -> params == other.params && expr == other.expr
    }

    override fun hashCode() = Objects.hash(params, expr)
}

internal class LocalVarExpr(val localVar: LocalVar, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is LocalVarExpr -> false
        else -> localVar == other.localVar
    }

    override fun hashCode() = Objects.hash(localVar)
}

internal class GlobalVarExpr(val value: Any, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is GlobalVarExpr -> false
        else -> value == other.value
    }

    override fun hashCode() = Objects.hash(value)
}

internal class DefExpr(val sym: Symbol, val expr: ValueExpr, override val loc: SourceSection?) : Expr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is DefExpr -> false
        else -> sym == other.sym && expr == other.expr
    }

    override fun hashCode() = Objects.hash(sym, expr)
}