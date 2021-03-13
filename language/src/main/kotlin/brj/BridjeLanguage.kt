package brj

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.TruffleObject

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.2",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    override fun createContext(truffleEnv: Env) = BridjeContext(truffleEnv)

    override fun parse(request: ParsingRequest): CallTarget {
        val forms = FormReader(request.source).use { it.readForms().toList() }
        return Truffle.getRuntime().createCallTarget(EvalRootNode(this, forms))
    }

    override fun getScope(context: BridjeContext): TruffleObject = context.bridjeEnv
}
