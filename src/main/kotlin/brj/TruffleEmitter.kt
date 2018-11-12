package brj

import brj.BridjeTypesGen.expectBoolean
import brj.BridjeTypesGen.expectCallTarget
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.`object`.*
import com.oracle.truffle.api.dsl.NodeChild
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

abstract class ValueNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any
}

class BoolNode(val boolean: Boolean) : ValueNode() {
    override fun execute(frame: VirtualFrame): Boolean = boolean
}

class IntNode(val int: Long) : ValueNode() {
    override fun execute(frame: VirtualFrame): Long = int
}

class FloatNode(val float: Double) : ValueNode() {
    override fun execute(frame: VirtualFrame): Double = float
}

class ObjectNode(val obj: Any) : ValueNode() {
    override fun execute(frame: VirtualFrame): Any = obj
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

class DoNode(@Children val exprNodes: Array<ValueNode>, @Child var exprNode: ValueNode) : ValueNode() {
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

class LetNode(@Children val bindingNodes: Array<UnitNode>, @Child var bodyNode: ValueNode) : ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val bindingCount = bindingNodes.size
        CompilerAsserts.compilationConstant<Int>(bindingCount)

        for (node in bindingNodes) {
            node.execute(frame)
        }

        return bodyNode.execute(frame)
    }
}


abstract class UnitNode : Node() {
    abstract fun execute(frame: VirtualFrame)
}

class ReadArgNode(val idx: Int) : ValueNode() {
    override fun execute(frame: VirtualFrame) = frame.arguments[idx]
}

@NodeChild("value", type = ValueNode::class)
@NodeField(name = "slot", type = FrameSlot::class)
abstract class WriteLocalVarNode : UnitNode() {
    abstract fun getSlot(): FrameSlot

    @Specialization
    fun writeObject(frame: VirtualFrame, value: Any) {
        frame.setObject(getSlot(), value)
    }
}

class CallNode(@Child var fnNode: ValueNode, @Children val argNodes: Array<ValueNode>) : ValueNode() {
    @Child
    var callNode = Truffle.getRuntime().createIndirectCallNode()

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val fn = expectCallTarget(fnNode.execute(frame))

        val argValues = arrayOfNulls<Any>(argNodes.size)

        for (i in argNodes.indices) {
            argValues[i] = argNodes[i].execute(frame)
        }

        return callNode.call(fn, argValues)
    }
}

@NodeField(name = "slot", type = FrameSlot::class)
abstract class ReadLocalVarNode : ValueNode() {
    abstract fun getSlot(): FrameSlot

    @Specialization
    protected fun read(frame: VirtualFrame): Any = FrameUtil.getObjectSafe(frame, getSlot())
}

data class ValueNodeEmitter(val lang: BrjLanguage, val frameDescriptor: FrameDescriptor) {
    inner class EvalRootNode(@Child var node: ValueNode) : RootNode(lang, frameDescriptor) {
        override fun execute(frame: VirtualFrame): Any {
            val res = node.execute(frame)

            return when (res) {
                is Boolean, is Long, is String -> res
                else -> lang.contextReference.get().truffleEnv.asGuestValue(res)
            }
        }
    }

    inner class FnBodyNode(expr: FnExpr) : RootNode(lang, frameDescriptor) {
        @Children
        val readArgNodes: Array<UnitNode> = expr.params
            .mapIndexed { idx, it -> WriteLocalVarNodeGen.create(ReadArgNode(idx), frameDescriptor.findOrAddFrameSlot(it)) }
            .toTypedArray()

        @Child
        var bodyNode: ValueNode = emitValueExpr(expr.expr)

        override fun execute(frame: VirtualFrame): Any {
            for (node in readArgNodes) {
                node.execute(frame)
            }

            return bodyNode.execute(frame)
        }
    }

    fun emitValueExpr(expr: ValueExpr): ValueNode =
        when (expr) {
            is BooleanExpr -> BoolNode(expr.boolean)
            is StringExpr -> ObjectNode(expr.string)
            is IntExpr -> IntNode(expr.int)
            is BigIntExpr -> ObjectNode(expr.bigInt)
            is FloatExpr -> FloatNode(expr.float)
            is BigFloatExpr -> ObjectNode(expr.bigFloat)

            is VectorExpr -> CollNode(expr.exprs.map(::emitValueExpr).toTypedArray()) { TreePVector.from(it) }

            is SetExpr -> CollNode(expr.exprs.map(::emitValueExpr).toTypedArray()) { HashTreePSet.from(it) }

            is FnExpr -> ObjectNode(Truffle.getRuntime().createCallTarget(ValueNodeEmitter(lang, FrameDescriptor()).FnBodyNode(expr)))

            is CallExpr -> CallNode(emitValueExpr(expr.f), expr.args.map(::emitValueExpr).toTypedArray())

            is IfExpr -> IfNode(
                emitValueExpr(expr.predExpr),
                emitValueExpr(expr.thenExpr),
                emitValueExpr(expr.elseExpr))

            is DoExpr -> DoNode(expr.exprs.map(::emitValueExpr).toTypedArray(), emitValueExpr(expr.expr))

            is LetExpr -> LetNode(
                expr.bindings
                    .map { WriteLocalVarNodeGen.create(emitValueExpr(it.expr), frameDescriptor.findOrAddFrameSlot(it.localVar)) }
                    .toTypedArray(),
                emitValueExpr(expr.expr))

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findFrameSlot(expr.localVar))
            is GlobalVarExpr -> ObjectNode(expr.globalVar.value!!)

            is CaseExpr -> TODO()
        }

    companion object {
        val LAYOUT = Layout.createLayout()!!
    }

    inner class FunctionConstructor(sym: Symbol, val paramTypes: List<Types.MonoType>) : RootNode(lang) {
        val constructorType = object : ObjectType() {
            @TruffleBoundary
            override fun toString(obj: DynamicObject): String = "($sym ${paramTypes.mapIndexed { idx, _ -> obj[idx] }.joinToString(" ")})"
        }

        private val factory: DynamicObjectFactory

        init {
            val allocator = LAYOUT.createAllocator()
            var shape = LAYOUT.createShape(constructorType)

            paramTypes.forEachIndexed { idx, paramType ->
                shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
            }

            factory = shape.createFactory()
        }

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val params = arrayOfNulls<Any>(paramTypes.size)

            for (i in paramTypes.indices) {
                params[i] = frame.arguments[i]
            }

            return factory.newInstance(*params)
        }
    }

    internal fun emitConstructor(constructor: ActionExprAnalyser.DefDataExpr.DefDataConstructor): Any =
        if (constructor.params != null)
            Truffle.getRuntime().createCallTarget(FunctionConstructor(constructor.sym, constructor.params))
        else object : Any() {
            override fun toString(): String = constructor.sym.toString()
        }
}