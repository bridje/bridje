package brj.nodes

import brj.BridjeNode
import brj.runtime.BridjeNull
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.source.SourceSection

sealed class CaseBranchNode(loc: SourceSection? = null) : BridjeNode(loc) {
    abstract fun tryMatch(frame: VirtualFrame, scrutinee: Any, interop: InteropLibrary): Any?
}

class TagBranchNode(
    private val expectedMeta: Any,
    private val bindingSlots: IntArray,
    @field:Child private var bodyNode: BridjeNode,
    loc: SourceSection? = null
) : CaseBranchNode(loc) {

    override fun execute(frame: VirtualFrame): Any? =
        error("TagBranchNode.execute should not be called directly")

    override fun tryMatch(frame: VirtualFrame, scrutinee: Any, interop: InteropLibrary): Any? {
        if (!interop.hasMetaObject(scrutinee)) return null

        val metaObject = interop.getMetaObject(scrutinee)
        if (metaObject !== expectedMeta) return null

        // For values with array elements (tagged tuples), extract bindings
        if (bindingSlots.isNotEmpty()) {
            if (!interop.hasArrayElements(scrutinee)) return null

            val arraySize = interop.getArraySize(scrutinee)
            if (arraySize != bindingSlots.size.toLong()) return null

            for (i in bindingSlots.indices) {
                val value = interop.readArrayElement(scrutinee, i.toLong())
                frame.setObject(bindingSlots[i], value)
            }
        }

        return bodyNode.execute(frame)
    }
}

class DefaultBranchNode(
    @field:Child private var bodyNode: BridjeNode,
    loc: SourceSection? = null
) : CaseBranchNode(loc) {

    override fun execute(frame: VirtualFrame): Any? =
        error("DefaultBranchNode.execute should not be called directly")

    override fun tryMatch(frame: VirtualFrame, scrutinee: Any, interop: InteropLibrary): Any? =
        bodyNode.execute(frame)
}

class NilBranchNode(
    @field:Child private var bodyNode: BridjeNode,
    loc: SourceSection? = null
) : CaseBranchNode(loc) {

    override fun execute(frame: VirtualFrame): Any? =
        error("NilBranchNode.execute should not be called directly")

    override fun tryMatch(frame: VirtualFrame, scrutinee: Any, interop: InteropLibrary): Any? =
        if (interop.isNull(scrutinee)) bodyNode.execute(frame) else null
}

class CatchAllBindingBranchNode(
    private val bindingSlot: Int,
    @field:Child private var bodyNode: BridjeNode,
    loc: SourceSection? = null
) : CaseBranchNode(loc) {

    override fun execute(frame: VirtualFrame): Any? =
        error("CatchAllBindingBranchNode.execute should not be called directly")

    override fun tryMatch(frame: VirtualFrame, scrutinee: Any, interop: InteropLibrary): Any? {
        if (interop.isNull(scrutinee)) return null
        frame.setObject(bindingSlot, scrutinee)
        return bodyNode.execute(frame)
    }
}

class CaseNode(
    @field:Child private var scrutineeNode: BridjeNode,
    @field:Children private val branchNodes: Array<CaseBranchNode>,
    loc: SourceSection? = null
) : BridjeNode(loc) {

    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any? {
        val scrutinee = scrutineeNode.execute(frame) ?: BridjeNull

        for (branch in branchNodes) {
            val result = branch.tryMatch(frame, scrutinee, interop)
            if (result != null || branch is DefaultBranchNode) {
                return result
            }
        }

        error("No matching case branch for: $scrutinee")
    }
}
