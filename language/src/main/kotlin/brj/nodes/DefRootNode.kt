package brj.nodes

import brj.BridjeLanguage
import brj.Typing
import brj.runtime.NsContext
import brj.runtime.Symbol
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection

@NodeChild(value = "expr", type = ExprNode::class)
internal abstract class DefRootNode(
    lang: BridjeLanguage, frameDescriptor: FrameDescriptor,
    private val nsCtx: NsContext,
    private val sym: Symbol,
    private val typing: Typing,
    private val loc: SourceSection?
) : RootNode(lang, frameDescriptor) {

    @Specialization
    fun doExecute(exprVal: Any): Any {
        nsCtx.def(sym, typing, exprVal)
        return exprVal
    }

    override fun getSourceSection() = loc
}