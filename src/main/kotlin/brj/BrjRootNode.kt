package brj

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class BrjRootNode(language: TruffleLanguage<BrjContext>, val forms: List<Form>) : RootNode(language) {
    override fun execute(virtualFrame: VirtualFrame) = forms.toString()
}
