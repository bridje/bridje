package brj.builtins

import brj.BridjeLanguage
import brj.SymbolForm
import brj.runtime.BridjeContext
import brj.runtime.BridjeRecord
import brj.runtime.Symbol
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class GensymNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val ctx = BridjeContext.get(this)
        val id = ctx.nextGensymId()

        val arg = frame.arguments[0]
        val prefix = if (arg is BridjeRecord) "G" else arg.toString()

        return SymbolForm(Symbol.intern("${prefix}__${id}"))
    }
}
