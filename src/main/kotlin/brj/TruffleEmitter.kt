package brj

import brj.BrjLanguage.Companion.getLang
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.RootNode
import java.math.BigDecimal
import java.math.BigInteger

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Double::class,
    BigInteger::class, BigDecimal::class,
    BridjeFunction::class, DataObject::class)
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
    override fun accessExecute(argumentsLength: Int) = Truffle.getRuntime().createCallTarget(object : RootNode(null) {

        @Child
        var callNode = Truffle.getRuntime().createIndirectCallNode()

        override fun execute(frame: VirtualFrame) =
        // FIXME I don't reckon this is very performant
            callNode.call((
                frame.arguments[0] as BridjeFunction).callTarget,
                frame.arguments.sliceArray(1 until frame.arguments.size))
    })
})!!

internal class BridjeFunction internal constructor(rootNode: RootNode) : TruffleObject {
    val callTarget = Truffle.getRuntime().createCallTarget(rootNode)

    override fun getForeignAccess() = functionForeignAccess
}

