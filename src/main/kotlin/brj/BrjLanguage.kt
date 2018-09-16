package brj

import brj.Analyser.Companion.analyseValueForm
import brj.BrjLanguage.Env
import brj.Reader.readForms
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage

@TruffleLanguage.Registration(id = "brj", name = "Bridje", defaultMimeType = "application/brj", characterMimeTypes = ["application/brj"])
class BrjLanguage : TruffleLanguage<Env>() {

    data class Env(val truffleEnv: TruffleLanguage.Env)

    override fun createContext(env: TruffleLanguage.Env) = Env(env)

    override fun isObjectOfLanguage(obj: Any): Boolean = false

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val form = readForms(request.source).first()

        return Truffle.getRuntime()
            .createCallTarget(GraalEmitter(this).emitValueExpr(analyseValueForm(form)))
    }

    fun asBrjValue(obj: Any): Any = contextReference.get().truffleEnv.asGuestValue(obj)
}
