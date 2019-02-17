package brj

import brj.BridjeTypesGen.*
import brj.BrjLanguage.Companion.getCtx
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
import com.oracle.truffle.api.interop.ForeignAccess.sendExecute
import com.oracle.truffle.api.interop.ForeignAccess.sendRead
import com.oracle.truffle.api.interop.Message
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
    BridjeFunction::class, RecordObject::class, VariantObject::class)
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

private fun constantly(obj: Any) = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(obj))

private val functionForeignAccess = ForeignAccess.create(BridjeFunction::class.java, object : ForeignAccess.StandardFactory {
    override fun accessIsExecutable() = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true))
    override fun accessExecute(argumentsLength: Int) = Truffle.getRuntime().createCallTarget(object : RootNode(getLang()) {

        @Child
        var callNode = Truffle.getRuntime().createIndirectCallNode()

        override fun execute(frame: VirtualFrame) =
        // FIXME I don't reckon this is very performant
            callNode.call(
                expectBridjeFunction(frame.arguments[0]).callTarget,
                frame.arguments.sliceArray(1 until frame.arguments.size))
    })
})!!

internal class BridjeFunction internal constructor(rootNode: RootNode) : TruffleObject {
    val callTarget = Truffle.getRuntime().createCallTarget(rootNode)

    override fun getForeignAccess() = functionForeignAccess
}

class RecordObject(val keys: List<RecordKey>, val dynamicObject: DynamicObject, private val faf: ForeignAccess.StandardFactory) : TruffleObject {
    override fun getForeignAccess() = ForeignAccess.create(RecordObject::class.java, faf)

    @TruffleBoundary
    override fun toString(): String = "{${keys.joinToString(", ") { key -> "${key.sym} ${dynamicObject[key]}" }}}"
}

internal class RecordKeyReadNode(val recordKey: RecordKey) : ValueNode() {
    @Child
    var readArgNode = ReadArgNode(0)

    override fun execute(frame: VirtualFrame) = expectRecordObject(readArgNode.execute(frame)).dynamicObject[recordKey.sym.toString()]
}

internal class RecordKeyInteropReadNode : ValueNode() {
    override fun execute(frame: VirtualFrame) = expectRecordObject(frame.arguments[0]).dynamicObject[expectString(frame.arguments[1])]
}

typealias RecordObjectFactory = (Array<Any?>) -> RecordObject

internal object RecordEmitter {
    private val LAYOUT = Layout.createLayout()

    internal data class RecordObjectType(val keys: Set<RecordKey>) : ObjectType()

    internal fun recordObjectFactory(keys: List<RecordKey>): RecordObjectFactory {
        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(RecordObjectType(keys.toSet()))

        keys.forEach { key ->
            shape = shape.addProperty(Property.create(key.sym.toString(), allocator.locationForType(key.type.javaType), 0))
        }

        val factory = shape.createFactory()

        val faf = object : ForeignAccess.StandardFactory {
            override fun accessHasSize() = constantly(true)
            override fun accessGetSize() = constantly(keys.size)
            override fun accessHasKeys() = constantly(true)
            override fun accessKeys() = constantly(getCtx().truffleEnv.asGuestValue(keys.map { it.sym.toString() }.toList()))

            override fun accessRead(): CallTarget = Truffle.getRuntime().createCallTarget(makeRootNode(RecordKeyInteropReadNode()))
        }

        return { vals ->
            RecordObject(keys, factory.newInstance(*vals), faf)
        }
    }

    internal fun emitRecordKey(recordKey: RecordKey) = BridjeFunction(makeRootNode(RecordKeyReadNode(recordKey), FrameDescriptor()))
}


class VariantObject(val variantKey: VariantKey, val dynamicObject: DynamicObject, private val faf: ForeignAccess.StandardFactory) : TruffleObject {
    override fun getForeignAccess() = ForeignAccess.create(VariantObject::class.java, faf)!!

    @TruffleBoundary
    override fun toString(): String =
        if (variantKey.paramTypes.isEmpty())
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
    private val paramCount = variantKey.paramTypes.size

    override fun execute(frame: VirtualFrame): Any {
        val obj = expectVariantObject(frame.arguments[0])
        val idx = expectLong(frame.arguments[1]).toInt()

        return if (idx < paramCount) obj.dynamicObject[idx] else throw IndexOutOfBoundsException(idx)
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

    internal data class VariantObjectType(val variantKey: VariantKey) : ObjectType()

    private fun objectFactory(variantKey: VariantKey): VariantObjectFactory {
        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(VariantObjectType(variantKey))

        variantKey.paramTypes.forEachIndexed { idx, paramType ->
            shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
        }

        val factory = shape.createFactory()

        val faf = object : ForeignAccess.StandardFactory {
            override fun accessHasSize(): CallTarget = constantly(true)
            override fun accessGetSize(): CallTarget? = constantly(variantKey.paramTypes.size)

            override fun accessRead(): CallTarget = Truffle.getRuntime().createCallTarget(makeRootNode(VariantKeyInteropReadNode(variantKey)))
        }

        return { args -> VariantObject(variantKey, factory.newInstance(*args), faf) }
    }

    fun emitVariantKey(variantKey: VariantKey): TruffleObject {
        val variantObjectFactory = objectFactory(variantKey)
        return if (variantKey.paramTypes.isNotEmpty())
            BridjeFunction(makeRootNode(VariantConstructorNode(variantObjectFactory, variantKey.paramTypes)))
        else
            variantObjectFactory(emptyArray())

    }
}

internal abstract class JavaInteropNode : ValueNode() {
    abstract override fun execute(frame: VirtualFrame): TruffleObject
}

internal class JavaStaticReadNode(javaImport: JavaImport) : JavaInteropNode() {
    val clazzObj = getCtx().truffleEnv.lookupHostSymbol(javaImport.clazz.name) as TruffleObject
    val name = javaImport.sym.base.baseStr

    @Child
    var readNode = Message.READ.createNode()

    override fun execute(frame: VirtualFrame) = sendRead(readNode, clazzObj, name) as TruffleObject
}

internal class JavaExecuteNode(@Child var fnNode: JavaInteropNode, javaImport: JavaImport) : ValueNode() {
    @Children
    val argNodes = (0 until (javaImport.type.monoType as FnType).paramTypes.size).map { idx -> ReadArgNode(idx) }.toTypedArray()

    @Child
    var executeNode = Message.EXECUTE.createNode()

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(argNodes.size)

        for (i in argNodes.indices) {
            params[i] = argNodes[i].execute(frame)
        }

        return sendExecute(executeNode, fnNode.execute(frame), *params)
    }
}

internal object JavaImportEmitter {
    fun emitJavaImport(javaImport: JavaImport): BridjeFunction =
        BridjeFunction(makeRootNode(JavaExecuteNode(JavaStaticReadNode(javaImport), javaImport)))
}
