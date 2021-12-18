package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.dsl.Specialization
import java.io.PrintWriter

private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

@BuiltIn("println!")
abstract class PrintlnNode(lang: BridjeLanguage) : BuiltInFn(lang) {

    @TruffleBoundary
    private fun print(env: TruffleLanguage.Env, str: String): String {
        PrintWriter(env.out()).run {
            println(str)
            flush()
        }
        return str
    }

    @Specialization
    fun doExecute(arg: String) =
        print(CTX_REF[this].truffleEnv, arg)
}