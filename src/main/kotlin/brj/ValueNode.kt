package brj

import brj.BridjeTypesGen.expectBoolean
import brj.BridjeTypesGen.expectBridjeFunction
import brj.ValueExpr.*
import brj.ValueNode.LetNode.LetBindingNode
import brj.ValueNodeFactory.LocalVarNodeGen
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.profiles.ConditionProfile
import org.pcollections.HashTreePSet
import org.pcollections.TreePVector
import java.math.BigDecimal
import java.math.BigInteger

sealed class ValueNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any

    class BoolNode(val boolean: Boolean) : ValueNode() {
        override fun execute(frame: VirtualFrame): Boolean = boolean
    }

    class StringNode(val string: String) : ValueNode() {
        override fun execute(frame: VirtualFrame): String = string
    }

    class IntNode(val int: Long) : ValueNode() {
        override fun execute(frame: VirtualFrame): Long = int
    }

    class BigIntNode(val bigInt: BigInteger) : ValueNode() {
        override fun execute(frame: VirtualFrame): BigInteger = bigInt
    }

    class FloatNode(val float: Double) : ValueNode() {
        override fun execute(frame: VirtualFrame): Double = float
    }

    class BigFloatNode(val bigDec: BigDecimal) : ValueNode() {
        override fun execute(frame: VirtualFrame): BigDecimal = bigDec
    }

    class CollNode(@Children val nodes: Array<ValueNode>, val toColl: (List<Any?>) -> Any) : ValueNode() {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val coll: MutableList<Any?> = ArrayList(nodes.size)

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
        private val conditionProfile = ConditionProfile.createBinaryProfile()

        override fun execute(frame: VirtualFrame): Any =
            (if (conditionProfile.profile(expectBoolean(predNode.execute(frame)))) thenNode else elseNode).execute(frame)
    }

    class DoNode(@Children var exprNodes: Array<ValueNode>, @Child var exprNode: ValueNode) : ValueNode() {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val exprCount = exprNodes.size
            CompilerAsserts.compilationConstant<Int>(exprCount)

            for (i in 0 until exprCount) {
                exprNodes[i].execute(frame)
            }

            return exprNode.execute(frame)
        }

    }

    class LetNode(@Children var bindingNodes: Array<LetBindingNode>, @Child var bodyNode: ValueNode) : ValueNode() {
        class LetBindingNode(val slot: FrameSlot, @Child var node: ValueNode) : Node() {
            fun execute(frame: VirtualFrame) {
                frame.setObject(slot, node.execute(frame))
            }
        }

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val bindingCount = bindingNodes.size
            CompilerAsserts.compilationConstant<Int>(bindingCount)

            for (i in 0 until bindingCount) {
                bindingNodes[i].execute(frame)
            }

            return bodyNode.execute(frame)
        }

    }

    class FnNode(val fn: BridjeFunction) : ValueNode() {

        class BridjeFunction(
            lang: BrjLanguage,
            frameDescriptor: FrameDescriptor,
            @Children var readArgNodes: Array<ReadArgNode>,
            @Child var bodyNode: ValueNode
        ) : RootNode(lang, frameDescriptor) {

            class ReadArgNode(val slot: FrameSlot, val idx: Int) : Node() {
                fun execute(frame: VirtualFrame) {
                    frame.setObject(slot, frame.arguments[idx])
                }
            }

            @ExplodeLoop
            override fun execute(frame: VirtualFrame): Any? {
                val argCount = readArgNodes.size
                CompilerAsserts.compilationConstant<Int>(argCount)
                for (i in 0 until argCount) {
                    readArgNodes[i].execute(frame)
                }

                return bodyNode.execute(frame)
            }

            fun callTarget(): CallTarget {
                return Truffle.getRuntime().createCallTarget(this)
            }
        }

        override fun execute(frame: VirtualFrame): BridjeFunction = fn
    }

    class CallNode(@Child var fnNode: ValueNode, @Children var argNodes: Array<ValueNode>) : ValueNode() {
        val argCount = argNodes.size

        @Child
        var callNode = Truffle.getRuntime().createIndirectCallNode()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val fn = expectBridjeFunction(fnNode.execute(frame))

            CompilerAsserts.compilationConstant<Int>(argCount)
            val argValues = Array<Any?>(argCount) { null }

            for (i in 0 until argCount) {
                argValues[i] = argNodes[i].execute(frame)
            }

            return callNode.call(fn.callTarget(), argValues)
        }
    }

    @NodeField(name = "slot", type = FrameSlot::class)
    abstract class LocalVarNode : ValueNode() {
        abstract fun getSlot(): FrameSlot

        @Specialization
        protected fun read(frame: VirtualFrame): Any = FrameUtil.getObjectSafe(frame, getSlot())
    }

    class GlobalVarNode(val obj: Any) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any = obj
    }

    data class ValueNodeEmitter(val lang: BrjLanguage, val frameDescriptor: FrameDescriptor) {

        inner class RootValueNode(@Child var node: ValueNode) : RootNode(lang, frameDescriptor) {
            override fun execute(frame: VirtualFrame): Any {
                val res = node.execute(frame)

                return when (res) {
                    is Boolean, is Long, is String -> res
                    else -> lang.contextReference.get().truffleEnv.asGuestValue(res)
                }
            }
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

                is FnExpr -> {
                    val slots = expr.params.associate { it to frameDescriptor.findOrAddFrameSlot(it) }
                    val readArgNodes = expr.params.mapIndexed { idx, it -> FnNode.BridjeFunction.ReadArgNode(slots[it]!!, idx) }

                    FnNode(FnNode.BridjeFunction(lang, frameDescriptor, readArgNodes.toTypedArray(), emitValueExpr(expr.expr)))
                }

                is CallExpr -> CallNode(emitValueExpr(expr.f), expr.args.map(::emitValueExpr).toTypedArray())

                is IfExpr -> IfNode(
                    emitValueExpr(expr.predExpr),
                    emitValueExpr(expr.thenExpr),
                    emitValueExpr(expr.elseExpr)
                )

                is DoExpr -> DoNode(expr.exprs.map(::emitValueExpr).toTypedArray(), emitValueExpr(expr.expr))

                is LetExpr -> {
                    val letBindingNodes = expr.bindings.map {
                        val node = emitValueExpr(it.expr)
                        val slot = frameDescriptor.findOrAddFrameSlot(it.localVar)
                        LetBindingNode(slot, node)
                    }

                    LetNode(letBindingNodes.toTypedArray(), emitValueExpr(expr.expr))
                }

                is LocalVarExpr -> LocalVarNodeGen.create(frameDescriptor.findFrameSlot(expr.localVar))

                is GlobalVarExpr -> GlobalVarNode(expr.globalVar.value!!)
            }

    }
}