package brj.nodes

import brj.BridjeNode
import brj.GlobalVar
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class GlobalVarNode(private val globalVar: GlobalVar, loc: SourceSection? = null) : BridjeNode(loc) {
    override fun execute(frame: VirtualFrame): Any? = globalVar.value
}
