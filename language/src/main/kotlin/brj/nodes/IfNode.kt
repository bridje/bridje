package brj.nodes

import brj.BridjeNode
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.profiles.CountingConditionProfile

class IfNode(
    @field:Child private var predNode: BridjeNode,
    @field:Child private var thenNode: BridjeNode,
    @field:Child private var elseNode: BridjeNode
) : BridjeNode() {

    private val conditionProfile = CountingConditionProfile.create()

    override fun execute(frame: VirtualFrame): Any? {
        val condition = predNode.executeBoolean(frame)
        return if (conditionProfile.profile(condition)) {
            thenNode.execute(frame)
        } else {
            elseNode.execute(frame)
        }
    }
}
