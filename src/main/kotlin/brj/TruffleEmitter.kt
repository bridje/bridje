package brj

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.Node
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

private val functionForeignAccess = ForeignAccess.create(BridjeFunction::class.java, object : ForeignAccess.StandardFactory {
    override fun accessIsExecutable() = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true))
    override fun accessExecute(argumentsLength: Int) = Truffle.getRuntime().createCallTarget(object : RootNode(null) {

        @Child
        var callNode = Truffle.getRuntime().createIndirectCallNode()

        override fun execute(frame: VirtualFrame) =
            // FIXME I don't reckon this is very performant
            callNode.call((
                frame.arguments[0] as BridjeFunction).callTarget,
                frame.arguments.sliceArray(1 until frame.arguments.size ))
    })
})!!

internal class BridjeFunction internal constructor(emitter: TruffleEmitter, bodyNode: ValueNode) : TruffleObject {
    val callTarget = emitter.makeCallTarget(bodyNode)

    override fun getForeignAccess() = functionForeignAccess
}

internal abstract class TruffleEmitter(val lang: BrjLanguage) {
    internal val frameDescriptor = FrameDescriptor()

    inner class RootValueNode(@Child var node: ValueNode) : RootNode(lang, frameDescriptor) {
        override fun execute(frame: VirtualFrame): Any = node.execute(frame)
    }

    fun makeCallTarget(node: ValueNode) = Truffle.getRuntime().createCallTarget(RootValueNode(node))!!
}