package brj

import brj.Polarity.*
import com.oracle.truffle.api.source.SourceSection

internal sealed class HeadType
internal object Int : HeadType()
internal object Bool : HeadType()
internal object Str : HeadType()
internal class Vec() : HeadType()
internal class Set() : HeadType()

internal enum class Polarity { INPUT, OUTPUT }

internal sealed class Transition
internal object EL : Transition()

internal class NodeClass(
    var headType: HeadType?,
    var transitions: Map<Transition, TypingNode> = emptyMap(),
    var nodes: kotlin.collections.Set<TypingNode> = emptySet(),
)

internal class TypingNode(
    val polarity: Polarity,
    var nodeClass: NodeClass,
    var flows: kotlin.collections.Set<TypingNode> = emptySet(),
    var transitions: kotlin.collections.Set<Transition> = emptySet(),
) {
    init {
        nodeClass.nodes += this
    }

    override fun toString() = when (nodeClass.headType) {
        Int -> "Int"
        Str -> "Str"
        Bool -> "Bool"
        is Vec -> "[${nodeClass.transitions[EL]}]"
        is Set -> "#{${nodeClass.transitions[EL]}}"
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

internal class IntExpr(val int: kotlin.Int, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is IntExpr && int == other.int)

    override fun hashCode() = int

    override val typing: Typing = Typing(res = TypingNode(OUTPUT, NodeClass(Int)))
}

internal class BoolExpr(val bool: Boolean, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is BoolExpr && bool == other.bool)

    override fun hashCode() = bool.hashCode()

    override val typing: Typing = Typing(res = TypingNode(OUTPUT, NodeClass(Bool)))
}

internal class StringExpr(val string: String, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is StringExpr && string == other.string)

    override fun hashCode() = string.hashCode()

    override fun toString() = "\"$string\""

    override val typing: Typing = Typing(res = TypingNode(OUTPUT, NodeClass(Str)))
}

private fun combineTypings(typings: Iterable<Typing>) {
    println("combine: $typings")
}

private fun NodeClass.unify(other: NodeClass) {
    if (this === other) return
    if (headType != null && other.headType != null && headType != other.headType) TODO()

    other.nodes.forEach { it.nodeClass = this }
}

private fun biunify(outType: TypingNode, inType: TypingNode) {
    // TODO
    println("biunify: $outType -> $inType")

    outType.nodeClass.unify(inType.nodeClass)
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

    combineTypings(typings)
    typings.forEach { biunify(it.res, elInput) }

    return Typing(resNode)
}

internal class VectorExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override val typing: Typing = collTyping(Vec(), exprs)
}

internal class SetExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override val typing: Typing = collTyping(Set(), exprs)
}

private fun doTyping(doExpr: DoExpr): Typing {
    combineTypings(doExpr.exprs.map { it.typing } + doExpr.expr.typing)
    return Typing(doExpr.expr.typing.res)
}

internal class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr, override val loc: SourceSection?) : ValueExpr() {
    override val typing = doTyping(this)
}

private val boolInput = TypingNode(INPUT, NodeClass(Bool))

private fun ifTyping(ifExpr: IfExpr): Typing {
    val (retInput, retOutput) = nodePair()
    val predTyping = ifExpr.predExpr.typing
    val thenTyping = ifExpr.thenExpr.typing
    val elseTyping = ifExpr.elseExpr.typing

    combineTypings(setOf(predTyping, thenTyping, elseTyping))
    biunify(predTyping.res, boolInput)
    biunify(thenTyping.res, retInput)
    biunify(elseTyping.res, retInput)
    return Typing(retOutput)
}

internal class IfExpr(
    val predExpr: ValueExpr,
    val thenExpr: ValueExpr,
    val elseExpr: ValueExpr,
    override val loc: SourceSection?,
) : ValueExpr() {
    override val typing = ifTyping(this)
}

internal class LetBinding(val binding: LocalVar, val expr: ValueExpr)

internal class LetExpr(
    val bindings: List<LetBinding>,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override val typing: Typing
        get() = TODO("Not yet implemented")
}

internal class LocalVarExpr(val localVar: LocalVar, override val loc: SourceSection?): ValueExpr() {
    // TODO
    override val typing: Typing = Typing(TypingNode(OUTPUT, NodeClass(Str)))

    override fun equals(other: Any?) =
        this === other || (other is LocalVarExpr && localVar == other.localVar)

    override fun hashCode() = localVar.hashCode()
}