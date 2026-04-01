package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeException
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class ThrowNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val value = frame.arguments[0]
        throw BridjeException(value, this)
    }
}
