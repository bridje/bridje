package brj

import brj.BridjeLanguage.BridjeContext
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

@Registration(
    id = "brj",
    name = "bridje",
    defaultMimeType = "text/brj",
    characterMimeTypes = ["text/brj"],
    contextPolicy = ContextPolicy.EXCLUSIVE
)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    class BridjeContext

    override fun createContext(env: Env) = BridjeContext()

    override fun parse(request: ParsingRequest?): CallTarget? {
        return object : RootNode(this) {
            override fun execute(frame: VirtualFrame?) = 42

        }.callTarget
    }
}