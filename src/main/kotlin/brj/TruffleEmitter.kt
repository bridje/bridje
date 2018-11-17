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

internal class CollNode(emitter: ValueNodeEmitter, exprs: List<ValueExpr>, private val collFn: (List<Any?>) -> Any) : ValueNode() {
    @Children
    val nodes = exprs.map(emitter::emitValueExpr).toTypedArray()

    @TruffleBoundary
    private fun toColl(list: List<Any?>) = collFn(list)

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val coll: MutableList<Any?> = ArrayList(nodes.size)

        for (node in nodes) {
            coll.add(node.execute(frame))
        }

        return toColl(coll)
    }
}

internal class DoNode(emitter: ValueNodeEmitter, expr: DoExpr) : ValueNode() {
    @Children
    val exprNodes = expr.exprs.map(emitter::emitValueExpr).toTypedArray()
    @Child
    var exprNode = emitter.emitValueExpr(expr.expr)

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

internal class IfNode(emitter: ValueNodeEmitter, expr: IfExpr) : ValueNode() {
    @Child
    var predNode = emitter.emitValueExpr(expr.predExpr)
    @Child
    var thenNode = emitter.emitValueExpr(expr.thenExpr)
    @Child
    var elseNode = emitter.emitValueExpr(expr.elseExpr)

    private val conditionProfile = ConditionProfile.createBinaryProfile()

    override fun execute(frame: VirtualFrame): Any =
        (if (conditionProfile.profile(expectBoolean(predNode.execute(frame)))) thenNode else elseNode).execute(frame)
}

internal class LetNode(emitter: ValueNodeEmitter, expr: LetExpr) : ValueNode() {
    @Children
    val bindingNodes = expr.bindings
        .map { WriteLocalVarNodeGen.create(emitter.emitValueExpr(it.expr), emitter.frameDescriptor.findOrAddFrameSlot(it.localVar)) }
        .toTypedArray()

    @Child
    var bodyNode: ValueNode = emitter.emitValueExpr(expr.expr)

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
internal abstract class WriteLocalVarNode : Node() {
    abstract fun getSlot(): FrameSlot

    @Specialization
    fun writeObject(frame: VirtualFrame, value: Any) {
        frame.setObject(getSlot(), value)
    }

    abstract fun execute(frame: VirtualFrame)
}

internal class FnBodyNode(emitter: ValueNodeEmitter, expr: FnExpr) : ValueNode() {
    @Children
    val readArgNodes = expr.params
        .mapIndexed { idx, it -> WriteLocalVarNodeGen.create(ReadArgNode(idx), emitter.frameDescriptor.findOrAddFrameSlot(it)) }
        .toTypedArray()

    @Child
    var bodyNode: ValueNode = emitter.emitValueExpr(expr.expr)

    override fun execute(frame: VirtualFrame): Any {
        for (node in readArgNodes) {
            node.execute(frame)
        }

        return bodyNode.execute(frame)
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

internal class CaseMatched(val res: Any) : ControlFlowException()

internal class CaseClauseNode(emitter: ValueNodeEmitter, dataSlot: FrameSlot, clause: CaseClause) : Node() {

    @Child
    var readSlot = ReadLocalVarNodeGen.create(dataSlot)!!

    @Children
    val writeBindingNodes =
        clause.bindings
            ?.mapIndexed { idx, lv ->
                WriteLocalVarNodeGen.create(
                    ReadDataTypeParamNode(ReadLocalVarNodeGen.create(dataSlot), idx),
                    emitter.frameDescriptor.findOrAddFrameSlot(lv))
            }
            ?.toTypedArray()
            ?: arrayOf()

    @Child
    var exprNode = emitter.emitValueExpr(clause.bodyExpr)

    private val conditionProfile = ConditionProfile.createBinaryProfile()!!
    private val constructorSym = clause.constructor.sym

    @ExplodeLoop
    fun execute(frame: VirtualFrame) {
        val value = expectDynamicObject(readSlot.execute(frame))

        if (conditionProfile.profile((value.shape.objectType as ValueNodeEmitter.ConstructorType).constructor.sym == constructorSym)) {
            for (node in writeBindingNodes) {
                node.execute(frame)
            }

            throw CaseMatched(exprNode.execute(frame))
        }
    }
}

internal class CaseExprNode(emitter: ValueNodeEmitter, expr: CaseExpr) : ValueNode() {
    private val dataSlot: FrameSlot = emitter.frameDescriptor.findOrAddFrameSlot(this)

    @Child
    var exprNode = WriteLocalVarNodeGen.create(emitter.emitValueExpr(expr.expr), dataSlot)

    @Children
    val clauseNodes = expr.clauses.map { CaseClauseNode(emitter, dataSlot, it) }.toTypedArray()

    @Child
    var defaultNode = expr.defaultExpr?.let(emitter::emitValueExpr)

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

internal class FunctionConstructorNode(private val factory: DynamicObjectFactory, private val paramTypes: List<MonoType>) : ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(paramTypes.size)

        for (i in paramTypes.indices) {
            params[i] = frame.arguments[i]
        }

        return factory.newInstance(*params)
    }
}

internal class DataTypeInteropReadNode(val constructor: DataTypeConstructor) : ValueNode() {
    private val paramCount = constructor.paramTypes?.size

    override fun execute(frame: VirtualFrame): Any {
        val obj = frame.arguments[0] as DynamicObject
        val idxArg = frame.arguments[1]

        return when (idxArg) {
            is Long -> {
                val idx = idxArg.toInt()
                if (paramCount == null || idx >= paramCount) throw IndexOutOfBoundsException(idx)

                obj[idx]
            }

            "constructor" -> constructor.sym.toString()
            "dataType" -> constructor.dataType.sym.toString()

            else -> TODO()
        }
    }
}

internal val LAYOUT = Layout.createLayout()


internal class ValueNodeEmitter(val lang: BrjLanguage, val frameDescriptor: FrameDescriptor) {

    inner class RootValueNode(@Child var node: ValueNode) : RootNode(lang, frameDescriptor) {
        override fun execute(frame: VirtualFrame): Any = node.execute(frame)
    }

    internal fun valueNodeCallTarget(node: ValueNode): CallTarget = Truffle.getRuntime().createCallTarget(RootValueNode(node))

    @Deprecated("shouldn't be necessary once we have full interop")
    inner class WrapGuestValueNode(@Child var node: ValueNode) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any {
            val res = node.execute(frame)

            return when (res) {
                is Boolean, is Long, is String, is TruffleObject -> res
                else -> lang.contextReference.get().truffleEnv.asGuestValue(res)
            }
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

            is VectorExpr -> CollNode(this, expr.exprs) { TreePVector.from(it) }

            is SetExpr -> CollNode(this, expr.exprs) { HashTreePSet.from(it) }

            is FnExpr -> {
                val emitter = ValueNodeEmitter(lang, FrameDescriptor())
                ObjectNode(emitter.valueNodeCallTarget(FnBodyNode(emitter, expr)))
            }

            is CallExpr -> CallNode(emitValueExpr(expr.f), expr.args.map(::emitValueExpr).toTypedArray())

            is IfExpr -> IfNode(this, expr)
            is DoExpr -> DoNode(this, expr)
            is LetExpr -> LetNode(this, expr)

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.localVar))
            is GlobalVarExpr -> ObjectNode(expr.globalVar.value!!)

            is CaseExpr -> CaseExprNode(this, expr)
        }

    inner class DataTypeForeignAccessFactory(val constructor: DataTypeConstructor) : ForeignAccess.StandardFactory {
        private fun constantly(obj: Any) = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(obj))

        override fun accessHasSize(): CallTarget = constantly(true)
        override fun accessGetSize(): CallTarget? = constructor.paramTypes?.let { constantly(it.size) }
        override fun accessHasKeys(): CallTarget = constantly(true)
        override fun accessRead(): CallTarget = Truffle.getRuntime().createCallTarget(RootValueNode(DataTypeInteropReadNode(constructor)))
    }

    inner class ConstructorType(val constructor: DataTypeConstructor) : ObjectType() {
        @TruffleBoundary
        override fun toString(obj: DynamicObject): String =
            if (constructor.paramTypes == null)
                constructor.sym.toString()
            else
                "(${constructor.sym} ${constructor.paramTypes.mapIndexed { idx, _ -> obj[idx] }.joinToString(" ")})"

        override fun getForeignAccessFactory(obj: DynamicObject): ForeignAccess =
            ForeignAccess.create(obj.javaClass, DataTypeForeignAccessFactory(constructor))
    }

    fun dynamicObjectFactory(constructor: DataTypeConstructor): DynamicObjectFactory {
        val constructorType = ConstructorType(constructor)

        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(constructorType)

        constructor.paramTypes?.forEachIndexed { idx, paramType ->
            shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
        }

        return shape.createFactory()
    }


    fun emitConstructor(constructor: DataTypeConstructor): ConstructorVar {
        val factory = dynamicObjectFactory(constructor)

        return ConstructorVar(constructor,
            if (constructor.paramTypes != null)
                valueNodeCallTarget(FunctionConstructorNode(factory, constructor.paramTypes))
            else
                factory.newInstance())
    }
}
