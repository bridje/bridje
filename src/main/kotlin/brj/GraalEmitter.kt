package brj

import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.profiles.ConditionProfile
import org.pcollections.HashTreePSet
import org.pcollections.PCollection
import org.pcollections.TreePVector
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

class GraalEmitter(val lang: BrjLanguage) {

    abstract inner class ValueNode : RootNode(lang)

    inner class BoolNode(val boolean: Boolean) : ValueNode() {
        override fun execute(frame: VirtualFrame): Boolean = boolean
    }

    inner class StringNode(val string: String) : ValueNode() {
        override fun execute(frame: VirtualFrame): String = string
    }

    inner class IntNode(val int: Long) : ValueNode() {
        override fun execute(frame: VirtualFrame): Long = int
    }

    inner class BigIntNode(val bigInt: BigInteger) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any = lang.asBrjValue(bigInt)
    }

    inner class FloatNode(val float: Double) : ValueNode() {
        override fun execute(frame: VirtualFrame): Double = float
    }

    inner class BigFloatNode(val bigDec: BigDecimal) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any = lang.asBrjValue(bigDec)
    }

    abstract inner class CollNode(exprs: List<ValueExpr>) : ValueNode() {
        @Children
        val nodes: Array<ValueNode> = exprs.map(::emitValueExpr).toTypedArray()

        abstract fun <T> toColl(coll: List<T>): PCollection<T>

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val coll: MutableList<Any> = ArrayList(nodes.size)

            for (node in nodes) {
                coll.add(node.execute(frame))
            }

            return lang.asBrjValue(toColl(coll))
        }
    }

    inner class VectorNode(exprs: List<ValueExpr>) : CollNode(exprs) {
        override fun <T> toColl(coll: List<T>): PCollection<T> = TreePVector.from(coll)
    }

    inner class SetNode(exprs: List<ValueExpr>) : CollNode(exprs) {
        override fun <T> toColl(coll: List<T>): PCollection<T> = HashTreePSet.from(coll)
    }

    inner class IfNode(predExpr: ValueExpr, thenExpr: ValueExpr, elseExpr: ValueExpr) : ValueNode() {
        private val conditionProfile = ConditionProfile.createBinaryProfile()!!
        private val predNode = emitValueExpr(predExpr)
        private val thenNode = emitValueExpr(thenExpr)
        private val elseNode = emitValueExpr(elseExpr)

        override fun execute(frame: VirtualFrame): Any {
            val result = predNode.execute(frame)

            if (result !is Boolean) {
                throw UnexpectedResultException(result)
            }

            return (if (conditionProfile.profile(result)) thenNode else elseNode).execute(frame)
        }
    }

    fun emitValueExpr(expr: ValueExpr): ValueNode =
        when (expr) {
            is BooleanExpr -> this.BoolNode(expr.boolean)
            is StringExpr -> StringNode(expr.string)
            is IntExpr -> IntNode(expr.int)
            is BigIntExpr -> BigIntNode(expr.bigInt)
            is FloatExpr -> FloatNode(expr.float)
            is BigFloatExpr -> BigFloatNode(expr.bigFloat)

            is VectorExpr -> VectorNode(expr.exprs)
            is SetExpr -> SetNode(expr.exprs)

            is CallExpr -> TODO()

            is IfExpr -> IfNode(expr.predExpr, expr.thenExpr, expr.elseExpr)
        }
}