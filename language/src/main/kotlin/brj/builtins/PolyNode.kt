package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.dsl.Specialization

private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

@BuiltIn("poly")
abstract class PolyNode(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doPoly(lang: String, code: String) =
        CTX_REF[this].poly(lang, code)
}