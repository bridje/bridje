package brj.builtins

import brj.BridjeLanguage
import brj.SymbolForm
import brj.runtime.BridjeContext
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class GensymNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val ctx = BridjeContext.get(this)
        val id = ctx.nextGensymId()

        val prefix = if (frame.arguments.isNotEmpty()) {
            val arg = frame.arguments[0]
            when (arg) {
                is String -> arg
                else -> arg.toString()
            }
        } else {
            "G"
        }

        return SymbolForm("${prefix}__${id}")
    }
}
