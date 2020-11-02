package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

internal class EvalExprsNode(
    lang: BridjeLanguage,

    @field:CompilationFinal(dimensions = 1)
    @field:Children
    private val nodes: Array<ExprNode>,
) : RootNode(lang) {

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        var res: Any? = null

        for (node in nodes) {
            res = node.execute(frame)
        }

        return res
    }
}