package brj

import brj.HeadType.*
import brj.Polarity.INPUT
import brj.Polarity.OUTPUT
import com.oracle.truffle.api.source.SourceSection

internal enum class HeadType { INT, BOOL, STRING, VECTOR, SET }
internal enum class Polarity { INPUT, OUTPUT }

internal class TypingNode(val headTypes: Set<HeadType>, val polarity: Polarity)

internal class Typing(val res: TypingNode)

internal sealed class ValueExpr {
    abstract val loc: SourceSection?
    abstract val typing: Typing
}

internal class IntExpr(val int: Int, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is IntExpr && int == other.int)

    override fun hashCode() = int

    override val typing: Typing = Typing(res = TypingNode(setOf(INT), OUTPUT))
}

internal class BoolExpr(val bool: Boolean, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is BoolExpr && bool == other.bool)

    override fun hashCode() = bool.hashCode()

    override val typing: Typing = Typing(res = TypingNode(setOf(BOOL), OUTPUT))
}

internal class StringExpr(val string: String, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is StringExpr && string == other.string)

    override fun hashCode() = string.hashCode()

    override fun toString() = "\"$string\""

    override val typing: Typing = Typing(res = TypingNode(setOf(STRING), OUTPUT))
}

private fun combineTypings(ret: TypingNode, typings: Iterable<Typing>): Typing {
    println("combine: $typings")
    // TODO
    return Typing(ret)
}

private data class BiunifyPair(val outNode: TypingNode, val inNode: TypingNode)

private fun biunify(typing: Typing, types: BiunifyPair): Typing {
    val (outNode, inNode) = types
    // TODO
    println("biunify: $types")
    return typing
}

private fun collTyping(collType: HeadType, exprs: List<ValueExpr>): Typing {
    val elInput = TypingNode(emptySet(), INPUT)
    val outNode = TypingNode(emptySet(), OUTPUT)

    val resNode = TypingNode(setOf(collType), OUTPUT)

    val typings = exprs.map { it.typing }
    val combinedTyping = combineTypings(resNode, typings)

    return typings.fold(combinedTyping) { acc, elTyping ->
        biunify(acc, BiunifyPair(elTyping.res, elInput))
    }
}

internal class VectorExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override val typing: Typing = collTyping(VECTOR, exprs)
}

internal class SetExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override val typing: Typing = collTyping(SET, exprs)
}
