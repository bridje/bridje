package brj

import brj.Analyser.analyseValueExpr
import brj.BrjLanguage.Env
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import brj.Reader.readForms
import brj.Types.valueExprTyping
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.Frame
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.profiles.ConditionProfile
import org.pcollections.HashTreePSet
import org.pcollections.PCollection
import org.pcollections.TreePVector

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"])
@Suppress("unused")
class BrjLanguage : TruffleLanguage<Env>() {

    data class Env(val truffleEnv: TruffleLanguage.Env)

    override fun createContext(env: TruffleLanguage.Env) = Env(env)

    override fun isObjectOfLanguage(obj: Any): Boolean = false

    data class LexicalScope(val slots: Map<Expr.LocalVar, FrameSlot> = emptyMap())

    abstract class ValueNode : Node() {
        abstract fun execute(frame: VirtualFrame): Any
    }

    class EmitterCtx(val lang: BrjLanguage, val lexicalScope: LexicalScope = LexicalScope()) {
        class BoolNode(val boolean: Boolean) : ValueNode() {
            override fun execute(frame: VirtualFrame): Boolean = boolean
        }

        class StringNode(val string: String) : ValueNode() {
            override fun execute(frame: VirtualFrame): String = string
        }

        class IntNode(val int: Long) : ValueNode() {
            override fun execute(frame: VirtualFrame): Long = int
        }

        class BigIntNode(val bigInt: Any) : ValueNode() {
            override fun execute(frame: VirtualFrame): Any = bigInt
        }

        class FloatNode(val float: Double) : ValueNode() {
            override fun execute(frame: VirtualFrame): Double = float
        }

        class BigFloatNode(val bigDec: Any) : ValueNode() {
            override fun execute(frame: VirtualFrame): Any = bigDec
        }

        class CollNode(@Children var nodes: Array<ValueNode>, val toColl: (List<Any>) -> PCollection<Any>) : ValueNode() {
            @ExplodeLoop
            override fun execute(frame: VirtualFrame): Any {
                val coll: MutableList<Any> = ArrayList(nodes.size)

                for (node in nodes) {
                    coll.add(node.execute(frame))
                }

                return toColl(coll)
            }
        }

        class IfNode(
            @Child var predNode: ValueNode,
            @Child var thenNode: ValueNode,
            @Child var elseNode: ValueNode
        ) : ValueNode() {
            private val conditionProfile = ConditionProfile.createBinaryProfile()!!

            override fun execute(frame: VirtualFrame): Any {
                val result = predNode.execute(frame)

                if (result !is Boolean) {
                    throw UnexpectedResultException(result)
                }

                return (if (conditionProfile.profile(result)) thenNode else elseNode).execute(frame)
            }
        }

        @NodeField(name = "slot", type = FrameSlot::class)
        abstract class LocalVarNode : ValueNode() {
            abstract fun getSlot(): FrameSlot

            private fun getLexicalScope(frame: Frame): Frame? = frame.arguments[0] as? Frame

            private fun <T> readVar(read: (Frame, FrameSlot) -> T, frame: Frame): T {
                var frame_: Frame = frame
                while (true) {
                    frame_ = getLexicalScope(frame_) ?: throw RuntimeException()
                    val value = read(frame_, getSlot())
                    if (value != null) return value
                }
            }

            @Specialization
            protected fun read(frame: VirtualFrame): Any = readVar(Frame::getValue, frame)
        }

        fun emitValueExpr(expr: ValueExpr): ValueNode =
            when (expr) {
                is BooleanExpr -> BoolNode(expr.boolean)
                is StringExpr -> StringNode(expr.string)
                is IntExpr -> IntNode(expr.int)
                is BigIntExpr -> BigIntNode(expr.bigInt)
                is FloatExpr -> FloatNode(expr.float)
                is BigFloatExpr -> BigFloatNode(expr.bigFloat)

                is VectorExpr -> CollNode(expr.exprs.map(::emitValueExpr).toTypedArray()) { TreePVector.from(it) }
                is SetExpr -> CollNode(expr.exprs.map(::emitValueExpr).toTypedArray()) { HashTreePSet.from(it) }

                is FnExpr -> TODO()
                is CallExpr -> TODO()

                is IfExpr -> IfNode(
                    emitValueExpr(expr.predExpr),
                    emitValueExpr(expr.thenExpr),
                    emitValueExpr(expr.elseExpr)
                )

                is LocalVarExpr -> TODO()
            }
    }

    inner class RootValueNode(val node: ValueNode) : RootNode(this) {
        override fun execute(frame: VirtualFrame): Any = node.execute(frame)
    }

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val form = readForms(request.source).first()

        val expr = analyseValueExpr(form)
        println("type: ${valueExprTyping(expr).returnType}")
        return Truffle.getRuntime().createCallTarget(RootValueNode(EmitterCtx(this).emitValueExpr(expr)))
    }
}
