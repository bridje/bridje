package brj

import brj.emitter.TruffleEmitter
import brj.reader.ClasspathLoader
import brj.reader.FormLoader
import brj.reader.nsForms
import brj.runtime.NSEnv
import brj.runtime.RuntimeEnv
import brj.runtime.Symbol
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.*
import com.oracle.truffle.api.TruffleLanguage

class BridjeContext internal constructor(internal val language: BridjeLanguage,
                                         internal val truffleEnv: TruffleLanguage.Env,
                                         internal var env: RuntimeEnv = RuntimeEnv()) {

    internal val formLoader: FormLoader = ClasspathLoader(this)

    @TruffleBoundary
    internal fun require(ns: Symbol): NSEnv {
        val evaluator = Evaluator(TruffleEmitter(this))

        synchronized(this) {
            env = nsForms(ns, formLoader)
                .fold(env) { env, forms ->
                    evaluator.evalNS(env, forms)
                }
        }

        return env.nses.getValue(ns)
    }
}