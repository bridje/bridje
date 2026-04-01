package brj.builtins

import brj.BridjeLanguage
import brj.runtime.Anomaly
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class ThrowNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val value = frame.arguments[0]
        throw value as Anomaly
    }
}
