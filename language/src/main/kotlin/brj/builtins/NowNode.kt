package brj.builtins

import brj.BridjeLanguage
import brj.nodes.ExprNode
import brj.runtime.BridjeInstant
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import java.time.Instant

class NowNode(lang: BridjeLanguage) : ExprNode(lang) {

    @TruffleBoundary
    private fun now() = BridjeInstant(Instant.now())

    override fun execute(frame: VirtualFrame?): Any = now()
}