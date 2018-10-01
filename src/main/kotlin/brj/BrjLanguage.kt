package brj

import brj.Analyser.analyseValueExpr
import brj.BrjLanguage.Env
import brj.Reader.readForms
import brj.Types.valueExprTyping
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"])
class BrjLanguage : TruffleLanguage<Env>() {

    data class Env(val truffleEnv: TruffleLanguage.Env)

    override fun createContext(env: TruffleLanguage.Env) = Env(env)

    override fun isObjectOfLanguage(obj: Any): Boolean = false

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val form = readForms(request.source).first()

        val expr = analyseValueExpr(form)
        println("type: ${valueExprTyping(expr).returnType}")
        return Truffle.getRuntime().createCallTarget(GraalEmitter(this).emitValueExpr(expr))
    }

    fun asBrjValue(obj: Any): Any = contextReference.get().truffleEnv.asGuestValue(obj)
}
