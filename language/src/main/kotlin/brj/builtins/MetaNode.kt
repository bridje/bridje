package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeRecord
import brj.runtime.Meta
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class MetaNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any =
        when (val obj = frame.arguments[0]) {
            is Meta<*> -> obj.meta
            else -> BridjeRecord.EMPTY
        }
}

class WithMetaNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val obj = frame.arguments[0]
        val newMeta = frame.arguments.getOrNull(1) as? BridjeRecord
        return when (obj) {
            is Meta<*> -> obj.withMeta(newMeta)
            else -> throw RuntimeException("withMeta: object does not support metadata")
        }
    }
}
