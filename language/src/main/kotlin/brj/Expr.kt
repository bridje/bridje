package brj

import com.oracle.truffle.api.source.SourceSection

sealed class ValueExpr {
    abstract val loc: SourceSection?
}

internal class IntExpr(val int: Int, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is IntExpr && int == other.int)

    override fun hashCode() = int
}

internal class BoolExpr(val bool: Boolean, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is BoolExpr && bool == other.bool)

    override fun hashCode() = bool.hashCode()
}

internal class StringExpr(val string: String, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is StringExpr && string == other.string)

    override fun hashCode() = string.hashCode()

    override fun toString() = "\"$string\""
}

internal class VectorExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr()
internal class SetExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr()
