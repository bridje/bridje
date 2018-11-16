package brj

import brj.BridjeTypesGen.*
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.`object`.*
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.ControlFlowException
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.profiles.ConditionProfile
import org.pcollections.HashTreePSet
import org.pcollections.TreePVector
import java.math.BigDecimal
import java.math.BigInteger

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Float::class, BigInteger::class, BigDecimal::class,
    CallTarget::class, DynamicObject::class)
internal abstract class BridjeTypes

internal abstract class ValueNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any
}

internal abstract class UnitNode : Node() {
    abstract fun execute(frame: VirtualFrame)
}

internal class BoolNode(val boolean: Boolean) : ValueNode() {
    override fun execute(frame: VirtualFrame): Boolean = boolean
}

internal class IntNode(val int: Long) : ValueNode() {
    override fun execute(frame: VirtualFrame): Long = int
}

internal class FloatNode(val float: Double) : ValueNode() {
    override fun execute(frame: VirtualFrame): Double = float
}

internal class ObjectNode(val obj: Any) : ValueNode() {
    override fun execute(frame: VirtualFrame): Any = obj
}

internal class CollNode(@Children val nodes: Array<ValueNode>, val toColl: (List<Any?>) -> Any) : ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val coll: MutableList<Any?> = ArrayList(nodes.size)

        for (node in nodes) {
            coll.add(node.execute(frame))
        }

        return toColl(coll)
    }
}

internal class DoNode(@Children val exprNodes: Array<ValueNode>, @Child var exprNode: ValueNode) : ValueNode() {
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

internal class LetNode(@Children val bindingNodes: Array<UnitNode>, @Child var bodyNode: ValueNode) : ValueNode() {
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

internal class ReadArgNode(val idx: Int) : ValueNode() {
    override fun execute(frame: VirtualFrame) = frame.arguments[idx]
}

@NodeChild("value", type = ValueNode::class)
@NodeField(name = "slot", type = FrameSlot::class)
internal abstract class WriteLocalVarNode : UnitNode() {
    abstract fun getSlot(): FrameSlot

    @Specialization
    fun writeObject(frame: VirtualFrame, value: Any) {
        frame.setObject(getSlot(), value)
    }
}

internal class CallNode(@Child var fnNode: ValueNode, @Children val argNodes: Array<ValueNode>) : ValueNode() {
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
internal abstract class ReadLocalVarNode : ValueNode() {
    abstract fun getSlot(): FrameSlot

    @Specialization
    protected fun read(frame: VirtualFrame): Any = FrameUtil.getObjectSafe(frame, getSlot())
}

internal class ReadDataTypeParamNode(@Child var objNode: ValueNode, val idx: Int) : ValueNode() {
    override fun execute(frame: VirtualFrame): Any {
        return expectDynamicObject(objNode.execute(frame))[idx]
    }
}

internal class ValueNodeEmitter(val lang: BrjLanguage, val frameDescriptor: FrameDescriptor) {

    inner class RootValueNode(@Child var node: ValueNode) : RootNode(lang, frameDescriptor) {
        override fun execute(frame: VirtualFrame): Any = node.execute(frame)
    }

    fun valueNodeCallTarget(node: ValueNode): CallTarget = Truffle.getRuntime().createCallTarget(RootValueNode(node))

    inner class WrapGuestValueNode(@Child var node: ValueNode) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any {
            val res = node.execute(frame)

            return when (res) {
                is Boolean, is Long, is String, is TruffleObject -> res
                else -> lang.contextReference.get().truffleEnv.asGuestValue(res)
            }
        }
    }

    inner class IfNode(expr: IfExpr) : ValueNode() {
        @Child var predNode = emitValueExpr(expr.predExpr)
        @Child var thenNode = emitValueExpr(expr.thenExpr)
        @Child var elseNode = emitValueExpr(expr.elseExpr)

        private val conditionProfile = ConditionProfile.createBinaryProfile()

        override fun execute(frame: VirtualFrame): Any =
            (if (conditionProfile.profile(expectBoolean(predNode.execute(frame)))) thenNode else elseNode).execute(frame)
    }

    inner class FnBodyNode(expr: FnExpr) : ValueNode() {
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

    inner class CaseExprNode(expr: CaseExpr) : ValueNode() {
        val slot: FrameSlot = frameDescriptor.findOrAddFrameSlot(this)

        inner class CaseMatched(val res: Any) : ControlFlowException()

        inner class CaseClauseNode(val clause: CaseClause) : Node() {
            @Child
            var readSlot = ReadLocalVarNodeGen.create(slot)

            @Children
            val writeBindingNodes =
                clause.bindings
                    ?.mapIndexed { idx, lv ->
                        WriteLocalVarNodeGen.create(
                            ReadDataTypeParamNode(ReadLocalVarNodeGen.create(slot), idx),
                            frameDescriptor.findOrAddFrameSlot(lv))
                    }
                    ?.toTypedArray()
                    ?: arrayOf()

            @Child
            var exprNode = emitValueExpr(clause.bodyExpr)

            val conditionProfile = ConditionProfile.createBinaryProfile()

            @ExplodeLoop
            fun execute(frame: VirtualFrame) {
                val value = expectDynamicObject(readSlot.execute(frame))

                if (conditionProfile.profile((value.shape.objectType as ConstructorType).sym == clause.constructor.sym)) {
                    for (node in writeBindingNodes) {
                        node.execute(frame)
                    }

                    throw CaseMatched(exprNode.execute(frame))
                }
            }
        }

        @Child
        var exprNode = WriteLocalVarNodeGen.create(emitValueExpr(expr.expr), slot)

        @Children
        val clauseNodes = expr.clauses.map(this@CaseExprNode::CaseClauseNode).toTypedArray()

        @Child
        var defaultNode = expr.defaultExpr?.let { emitValueExpr(it) }

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            exprNode.execute(frame)

            try {
                for (node in clauseNodes) {
                    node.execute(frame)
                }
            } catch (e: CaseMatched) {
                return e.res
            }

            return defaultNode?.execute(frame) ?: TODO()
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

            is FnExpr -> {
                val emitter = ValueNodeEmitter(lang, FrameDescriptor())
                ObjectNode(emitter.valueNodeCallTarget(emitter.FnBodyNode(expr)))
            }

            is CallExpr -> CallNode(emitValueExpr(expr.f), expr.args.map(::emitValueExpr).toTypedArray())

            is IfExpr -> IfNode(expr)

            is DoExpr -> DoNode(expr.exprs.map(::emitValueExpr).toTypedArray(), emitValueExpr(expr.expr))

            is LetExpr -> LetNode(
                expr.bindings
                    .map { WriteLocalVarNodeGen.create(emitValueExpr(it.expr), frameDescriptor.findOrAddFrameSlot(it.localVar)) }
                    .toTypedArray(),
                emitValueExpr(expr.expr))

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.localVar))
            is GlobalVarExpr -> ObjectNode(expr.globalVar.value!!)

            is CaseExpr -> CaseExprNode(expr)
        }

    companion object {
        val LAYOUT = Layout.createLayout()!!
    }

    class DataTypeInteropReadNode(val sym: QSymbol, val dataType: DataType, val paramTypes: List<MonoType>?) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any {
            val obj = frame.arguments[0] as DynamicObject
            val idxArg = frame.arguments[1]

            return when (idxArg) {
                is Long -> {
                    val idx = idxArg.toInt()
                    if (paramTypes == null || idx >= paramTypes.size) throw IndexOutOfBoundsException(idx)

                    obj[idx]
                }

                "constructor" -> sym.toString()
                "dataType" -> dataType.sym.toString()

                else -> TODO()
            }
        }
    }

    inner class DataTypeForeignAccessFactory(val sym: QSymbol, val dataType: DataType, val paramTypes: List<MonoType>?) : ForeignAccess.StandardFactory {
        override fun accessHasSize(): CallTarget = valueNodeCallTarget(BoolNode(paramTypes != null))
        override fun accessGetSize(): CallTarget? = paramTypes?.let { valueNodeCallTarget(IntNode(it.size.toLong())) }
        override fun accessHasKeys(): CallTarget = valueNodeCallTarget(BoolNode(true))
        override fun accessRead(): CallTarget = valueNodeCallTarget(DataTypeInteropReadNode(sym, dataType, paramTypes))
    }

    inner class ConstructorType(val sym: QSymbol, val dataType: DataType, val paramTypes: List<MonoType>?) : ObjectType() {
        @TruffleBoundary
        override fun toString(obj: DynamicObject): String =
            if (paramTypes == null)
                sym.toString()
            else
                "($sym ${paramTypes.mapIndexed { idx, _ -> obj[idx] }.joinToString(" ")})"

        override fun getForeignAccessFactory(obj: DynamicObject): ForeignAccess =
            ForeignAccess.create(obj.javaClass, DataTypeForeignAccessFactory(sym, dataType, paramTypes))
    }

    fun dynamicObjectFactory(sym: QSymbol, dataType: DataType, paramTypes: List<MonoType>?): DynamicObjectFactory {
        val constructorType = ConstructorType(sym, dataType, paramTypes)

        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(constructorType)

        paramTypes?.forEachIndexed { idx, paramType ->
            shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
        }

        return shape.createFactory()
    }

    inner class FunctionConstructor(private val factory: DynamicObjectFactory, private val paramTypes: List<MonoType>) : RootNode(lang) {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val params = arrayOfNulls<Any>(paramTypes.size)

            for (i in paramTypes.indices) {
                params[i] = frame.arguments[i]
            }

            return factory.newInstance(*params)
        }
    }

    fun emitConstructor(sym: QSymbol, dataType: DataType, paramTypes: List<MonoType>?): DataTypeConstructor {
        val factory = dynamicObjectFactory(sym, dataType, paramTypes)

        return DataTypeConstructor(sym, dataType, paramTypes,
            if (paramTypes != null)
                Truffle.getRuntime().createCallTarget(FunctionConstructor(factory, paramTypes))
            else
                factory.newInstance())
    }
}