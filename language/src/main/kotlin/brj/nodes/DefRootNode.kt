package brj.nodes

import brj.BridjeLanguage
import brj.Typing
import brj.runtime.Symbol
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection

private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

@NodeChild(value = "expr", type = ExprNode::class)
abstract class DefRootNode(lang: BridjeLanguage, frameDescriptor: FrameDescriptor,
                           private val sym: Symbol,
                           private val typing: Typing,
                           private val loc: SourceSection?) :
    RootNode(lang, frameDescriptor) {
    @Specialization
    fun doExecute(exprVal: Any): Any {
        CTX_REF[this].def(sym, typing, exprVal)
        return exprVal
    }

    override fun getSourceSection() = loc
}