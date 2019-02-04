package brj

import brj.BridjeTypesGen.expectVariantObject
import brj.BrjLanguage.Companion.getLang
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.Layout
import com.oracle.truffle.api.`object`.ObjectType
import com.oracle.truffle.api.`object`.Property
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.RootNode
import java.math.BigDecimal
import java.math.BigInteger

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Double::class,
    BigInteger::class, BigDecimal::class,
    BridjeFunction::class, VariantObject::class)
internal abstract class BridjeTypes

@TypeSystemReference(BridjeTypes::class)
@NodeInfo(language = "brj")
internal abstract class ValueNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any
}

internal class ReadArgNode(val idx: Int) : ValueNode() {
    override fun execute(frame: VirtualFrame) = frame.arguments[idx]
}

@NodeField(name = "slot", type = FrameSlot::class)
internal abstract class ReadLocalVarNode : ValueNode() {
    abstract fun getSlot(): FrameSlot


    @Specialization
    protected fun read(frame: VirtualFrame): Any = FrameUtil.getObjectSafe(frame, getSlot())
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

internal fun makeRootNode(node: ValueNode, frameDescriptor: FrameDescriptor = FrameDescriptor()) =
    object : RootNode(getLang(), frameDescriptor) {
        override fun execute(frame: VirtualFrame): Any = node.execute(frame)
    }

internal fun createCallTarget(rootNode: RootNode) = Truffle.getRuntime().createCallTarget(rootNode)

private val functionForeignAccess = ForeignAccess.create(BridjeFunction::class.java, object : ForeignAccess.StandardFactory {
    override fun accessIsExecutable() = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true))
    override fun accessExecute(argumentsLength: Int) = Truffle.getRuntime().createCallTarget(object : RootNode(getLang()) {

        @Child
        var callNode = Truffle.getRuntime().createIndirectCallNode()

        override fun execute(frame: VirtualFrame) =
        // FIXME I don't reckon this is very performant
            callNode.call(
                (frame.arguments[0] as BridjeFunction).callTarget,
                frame.arguments.sliceArray(1 until frame.arguments.size))
    })
})!!

internal class BridjeFunction internal constructor(rootNode: RootNode) : TruffleObject {
    val callTarget = Truffle.getRuntime().createCallTarget(rootNode)

    override fun getForeignAccess() = functionForeignAccess
}

class VariantObject(val variantKey: VariantKey, val dynamicObject: DynamicObject, private val faf: ForeignAccess.StandardFactory) : TruffleObject {
    override fun getForeignAccess() = ForeignAccess.create(VariantObject::class.java, faf)!!

    @TruffleBoundary
    override fun toString(): String =
        if (variantKey.paramTypes == null)
            variantKey.sym.toString()
        else
            "(${variantKey.sym} ${variantKey.paramTypes.mapIndexed { idx, _ -> dynamicObject[idx] }.joinToString(" ")})"
}

internal class ReadVariantParamNode(@Child var objNode: ValueNode, val idx: Int) : ValueNode() {
    override fun execute(frame: VirtualFrame): Any {
        return expectVariantObject(objNode.execute(frame)).dynamicObject[idx]
    }
}

internal class VariantKeyInteropReadNode(variantKey: VariantKey) : ValueNode() {
    private val paramCount = variantKey.paramTypes?.size

    override fun execute(frame: VirtualFrame): Any {
        val obj = frame.arguments[0] as VariantObject
        val idxArg = frame.arguments[1]
        return when (idxArg) {
            is Long -> {
                val idx = idxArg.toInt()
                if (paramCount == null || idx >= paramCount) throw IndexOutOfBoundsException(idx)

                obj.dynamicObject[idx]
            }

            else -> TODO()
        }
    }
}

typealias VariantObjectFactory = (Array<Any?>) -> VariantObject

internal class VariantConstructorNode(private val variantObjectFactory: VariantObjectFactory, private val paramTypes: List<MonoType>) : ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(paramTypes.size)

        for (i in paramTypes.indices) {
            params[i] = frame.arguments[i]
        }

        return variantObjectFactory(params)
    }
}

internal object VariantEmitter {
    private val LAYOUT = Layout.createLayout()

    internal class VariantObjectType(val variantKey: VariantKey) : ObjectType()

    private fun objectFactory(variantKey: VariantKey): VariantObjectFactory {
        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(VariantObjectType(variantKey))

        variantKey.paramTypes?.forEachIndexed { idx, paramType ->
            shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
        }

        val factory = shape.createFactory()

        return { args ->
            VariantObject(variantKey, factory.newInstance(*args), object : ForeignAccess.StandardFactory {
                private fun constantly(obj: Any) = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(obj))

                override fun accessHasSize(): CallTarget = constantly(true)
                override fun accessGetSize(): CallTarget? = variantKey.paramTypes?.let { constantly(it.size) }
                override fun accessHasKeys(): CallTarget = constantly(true)

                override fun accessRead(): CallTarget = Truffle.getRuntime().createCallTarget(makeRootNode(VariantKeyInteropReadNode(variantKey)))
            })
        }
    }

    fun emitVariant(variantKey: VariantKey): TruffleObject {
        val variantObjectFactory = objectFactory(variantKey)
        return if (variantKey.paramTypes != null)
            BridjeFunction(makeRootNode(VariantConstructorNode(variantObjectFactory, variantKey.paramTypes)))
        else
            variantObjectFactory(emptyArray())

    }
}
