package brj

import com.oracle.truffle.api.source.SourceSection
import java.util.*

internal sealed class ValueExpr {
    abstract val loc: SourceSection?
}

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

internal class LetBinding(val binding: LocalVar, val expr: ValueExpr)

internal class LetExpr(
    val bindings: List<LetBinding>,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr()

internal class LocalVarExpr(val localVar: LocalVar, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is LocalVarExpr && localVar == other.localVar)

    override fun hashCode() = Objects.hash(localVar)
}