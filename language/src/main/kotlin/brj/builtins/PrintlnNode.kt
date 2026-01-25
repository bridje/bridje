package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import java.io.PrintWriter

class PrintlnNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any? {
        val value = frame.arguments[0]
        val out = PrintWriter(BridjeContext.get(this).truffleEnv.out(), true)
        out.println(value)
        return value
    }
}
