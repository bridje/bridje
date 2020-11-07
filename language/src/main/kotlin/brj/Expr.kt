package brj

import brj.HeadType.*
import brj.Polarity.INPUT
import brj.Polarity.OUTPUT
import com.oracle.truffle.api.source.SourceSection

internal enum class HeadType { INT, BOOL, STRING, VECTOR, SET }
internal enum class Polarity { INPUT, OUTPUT }

internal sealed class Transition
internal object EL : Transition()

internal class NodeClass(
    var headType: HeadType?,
    var transitions: Map<Transition, TypingNode> = emptyMap(),
    var nodes: Set<TypingNode> = emptySet(),
)

internal class TypingNode(
    val polarity: Polarity,
    var nodeClass: NodeClass,
    var flows: Set<TypingNode> = emptySet(),
    var transitions: Set<Transition> = emptySet(),
) {
    init {
        nodeClass.nodes += this
    }

    override fun toString() = when (nodeClass.headType) {
        INT -> "Int"
        STRING -> "Str"
        BOOL -> "Bool"
        VECTOR -> "[${nodeClass.transitions[EL]}]"
        SET -> "#{${nodeClass.transitions[EL]}}"
        null -> "unk" // TODO
    }
}

internal data class Typing(val res: TypingNode) {
    override fun toString() = res.toString()
}

internal sealed class ValueExpr {
    abstract val loc: SourceSection?
    abstract val typing: Typing
}

internal class IntExpr(val int: Int, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is IntExpr && int == other.int)

    override fun hashCode() = int

    override val typing: Typing = Typing(res = TypingNode(OUTPUT, NodeClass(INT)))
}

internal class BoolExpr(val bool: Boolean, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is BoolExpr && bool == other.bool)

    override fun hashCode() = bool.hashCode()

    override val typing: Typing = Typing(res = TypingNode(OUTPUT, NodeClass(BOOL)))
}

internal class StringExpr(val string: String, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is StringExpr && string == other.string)

    override fun hashCode() = string.hashCode()

    override fun toString() = "\"$string\""

    override val typing: Typing = Typing(res = TypingNode(OUTPUT, NodeClass(STRING)))
}

private fun combineTypings(ret: TypingNode, typings: Iterable<Typing>): Typing {
    println("combine: $typings")
    // TODO
    return Typing(ret)
}

private fun TypingNode.mergeOut(o2: TypingNode) {
    flows += o2.flows
}

private fun TypingNode.mergeIn(o2: TypingNode) {
    println("this = ${this}")
    println("o2 = [${o2}]")
}

private fun NodeClass.unify(other: NodeClass) {
    if (this === other) return
    if (headType != null && other.headType != null && headType != other.headType) TODO()

    other.nodes.forEach { it.nodeClass = this }
}

private fun Typing.biunify(outType: TypingNode, inType: TypingNode): Typing {
    // TODO
    println("biunify: $outType -> $inType")

    outType.nodeClass.unify(inType.nodeClass)

    return this
}

private fun nodePair(headType: HeadType? = null): Pair<TypingNode, TypingNode> {
    val nodeClass = NodeClass(headType)
    val inNode = TypingNode(INPUT, nodeClass)
    val outNode = TypingNode(OUTPUT, nodeClass)

    inNode.flows += outNode
    outNode.flows += inNode

    return Pair(inNode, outNode)
}

private fun collTyping(collType: HeadType, exprs: List<ValueExpr>): Typing {
    val (elInput, elOutput) = nodePair()
    val resNode = TypingNode(OUTPUT, NodeClass(collType, mapOf(EL to elOutput)))

    val typings = exprs.map { it.typing }

    return typings.fold(combineTypings(resNode, typings)) { typing, elTyping -> typing.biunify(elTyping.res, elInput) }
}

internal class VectorExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override val typing: Typing = collTyping(VECTOR, exprs)
}

internal class SetExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override val typing: Typing = collTyping(SET, exprs)
}

private fun doTyping(doExpr: DoExpr) =
    combineTypings(doExpr.expr.typing.res, doExpr.exprs.map { it.typing } + doExpr.expr.typing)

internal class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr, override val loc: SourceSection?) : ValueExpr() {
    override val typing = doTyping(this)
}

private fun ifTyping(ifExpr: IfExpr): Typing {
    val (retInput, retOutput) = nodePair()
    val predTyping = ifExpr.predExpr.typing
    val thenTyping = ifExpr.thenExpr.typing
    val elseTyping = ifExpr.elseExpr.typing

    // TODO predTyping input to bool
    return combineTypings(retOutput, setOf(predTyping, thenTyping, elseTyping))
        .biunify(thenTyping.res, retInput)
        .biunify(elseTyping.res, retInput)
}

internal class IfExpr(
    val predExpr: ValueExpr,
    val thenExpr: ValueExpr,
    val elseExpr: ValueExpr,
    override val loc: SourceSection?,
) : ValueExpr() {
    override val typing = ifTyping(this)
}
