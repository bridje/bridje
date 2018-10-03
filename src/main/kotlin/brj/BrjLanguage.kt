package brj

import brj.Analyser.analyseValueExpr
import brj.BrjLanguage.EmitterCtx.BridjeFunction
import brj.BrjLanguage.Env
import brj.BrjLanguageFactory.EmitterCtxFactory.LocalVarNodeGen
import brj.Expr.ValueExpr
import brj.Expr.ValueExpr.*
import brj.Reader.readForms
import brj.Types.valueExprTyping
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.profiles.ConditionProfile
import org.pcollections.HashTreePSet
import org.pcollections.TreePVector
import java.math.BigDecimal
import java.math.BigInteger

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

    private fun asBridjeValue(obj: Any) = contextReference.get().truffleEnv.asGuestValue(obj)

    abstract class ValueNode : Node() {
        abstract fun execute(frame: VirtualFrame): Any
    }

    @TypeSystem(
        Boolean::class, String::class,
        Long::class, Float::class, BigInteger::class, BigDecimal::class,
        BridjeFunction::class,
        TreePVector::class, HashTreePSet::class)
    abstract class BridjeTypes

    data class EmitterCtx(val lang: BrjLanguage, val frameDescriptor: FrameDescriptor, val lexicalScope: LexicalScope = LexicalScope()) {
        data class LexicalScope(val slots: Map<Expr.LocalVar, FrameSlot> = emptyMap())

        class BoolNode(val boolean: Boolean) : ValueNode() {
            override fun execute(frame: VirtualFrame): Boolean = boolean
        }

        class StringNode(val string: String) : ValueNode() {
            override fun execute(frame: VirtualFrame): String = string
        }

        class IntNode(val int: Long) : ValueNode() {
            override fun execute(frame: VirtualFrame): Long = int
        }

        inner class BigIntNode(val bigInt: BigInteger) : ValueNode() {
            override fun execute(frame: VirtualFrame): Any = lang.asBridjeValue(bigInt)
        }

        class FloatNode(val float: Double) : ValueNode() {
            override fun execute(frame: VirtualFrame): Double = float
        }

        inner class BigFloatNode(val bigDec: BigDecimal) : ValueNode() {
            override fun execute(frame: VirtualFrame): Any = lang.asBridjeValue(bigDec)
        }

        inner class CollNode(@Children var nodes: Array<ValueNode>, val toColl: (List<Any>) -> Any) : ValueNode() {
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

        class BridjeFunction {
            fun callTarget(): CallTarget {
                TODO()
            }
        }

        class CallNode(@Child var fnNode: ValueNode, @Children var argNodes: Array<ValueNode>) : ValueNode() {
            val argCount = argNodes.size

            @Child
            var callNode = Truffle.getRuntime().createIndirectCallNode()

            @ExplodeLoop
            override fun execute(frame: VirtualFrame): Any {
                val fn = BridjeTypesGen.expectBridjeFunction(fnNode.execute(frame))

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

        fun emitValueExpr(expr: ValueExpr): ValueNode =
            when (expr) {
                is BooleanExpr -> BoolNode(expr.boolean)
                is StringExpr -> StringNode(expr.string)
                is IntExpr -> IntNode(expr.int)
                is BigIntExpr -> BigIntNode(expr.bigInt)
                is FloatExpr -> FloatNode(expr.float)
                is BigFloatExpr -> BigFloatNode(expr.bigFloat)

                is VectorExpr ->
                    CollNode(expr.exprs.map(::emitValueExpr).toTypedArray()) { TreePVector.from(it) }

                is SetExpr ->
                    CollNode(expr.exprs.map(::emitValueExpr).toTypedArray()) { HashTreePSet.from(it) }

                is FnExpr -> TODO()
                is CallExpr -> CallNode(emitValueExpr(expr.f), expr.args.map(::emitValueExpr).toTypedArray())

                is IfExpr -> IfNode(
                    emitValueExpr(expr.predExpr),
                    emitValueExpr(expr.thenExpr),
                    emitValueExpr(expr.elseExpr)
                )

                is Expr.ValueExpr.LetExpr -> {
                    var ctx = this
                    val letBindingNodes = expr.bindings.map {
                        val node = ctx.emitValueExpr(it.expr)
                        val slot = frameDescriptor.findOrAddFrameSlot(it.localVar)
                        ctx = ctx.copy(lexicalScope = LexicalScope(ctx.lexicalScope.slots.plus(Pair(it.localVar, slot))))
                        LetNode.LetBindingNode(slot, node)
                    }

                    LetNode(letBindingNodes.toTypedArray(), ctx.emitValueExpr(expr.expr))
                }

                is LocalVarExpr ->
                    LocalVarNodeGen.create(lexicalScope.slots[expr.localVar])
            }

    }

    inner class RootValueNode(@Children var nodes: Array<ValueNode>, frameDescriptor: FrameDescriptor) : RootNode(this, frameDescriptor) {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val secondToLast = nodes.size - 1
            CompilerAsserts.compilationConstant<Int>(secondToLast)

            for (i in 0 until secondToLast) {
                nodes[i].execute(frame)
            }

            val res = nodes[secondToLast].execute(frame)

            return when (res) {
                is Boolean, is Long, is String -> res
                else -> asBridjeValue(res)
            }
        }
    }

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val form = readForms(request.source).first()

        val expr = analyseValueExpr(form)
        println("type: ${valueExprTyping(expr).returnType}")

        val frameDescriptor = FrameDescriptor()
        return Truffle.getRuntime().createCallTarget(RootValueNode(arrayOf(EmitterCtx(this, frameDescriptor).emitValueExpr(expr)), frameDescriptor))
    }
}
