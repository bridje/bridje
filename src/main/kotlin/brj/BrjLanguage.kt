package brj

import brj.Analyser.Companion.analyseValueForm
import brj.GraalEmitter.emitValueExpr
import brj.Reader.readForms
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage

class BrjLanguage : TruffleLanguage<BrjContext>() {

    override fun createContext(env: TruffleLanguage.Env) = BrjContext()

    override fun isObjectOfLanguage(obj: Any): Boolean = true

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val form = readForms(request.source).first()

        return Truffle.getRuntime()
            .createCallTarget(emitValueExpr(this, analyseValueForm(form)))
    }
}
