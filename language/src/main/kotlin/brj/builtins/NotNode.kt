package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class NotNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        return !(frame.arguments[0] as Boolean)
    }
}
