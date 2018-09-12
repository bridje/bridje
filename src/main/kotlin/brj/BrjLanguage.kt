package brj

import brj.Reader.readForms
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage

class BrjLanguage : TruffleLanguage<BrjContext>() {

    override fun createContext(env: TruffleLanguage.Env) = BrjContext()

    override fun isObjectOfLanguage(obj: Any): Boolean = true

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        return Truffle.getRuntime()
            .createCallTarget(BrjRootNode(this, readForms(request.source)))
    }
}
