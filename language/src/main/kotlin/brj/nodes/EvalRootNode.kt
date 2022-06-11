package brj.nodes

import brj.BridjeLanguage
import brj.Form
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.RootNode

private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

internal abstract class EvalRootNode(lang: BridjeLanguage, private val forms: List<Form>) : RootNode(lang) {

    @Specialization
    @TruffleBoundary
    fun execute(): Any? {
        CompilerDirectives.transferToInterpreter()

        var res: Any? = null
        val ctx = CTX_REF[this]

        for (form in forms) {
            res = ctx.evalForm(form)
        }

        return res
    }
}
