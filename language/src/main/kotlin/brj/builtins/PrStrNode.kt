package brj.builtins

import brj.BridjeLanguage
import brj.BridjeTypesGen.expectString
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.TruffleLanguage.LanguageReference
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary

private val LANG_REF = LanguageReference.create(BridjeLanguage::class.java)
private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

@BuiltIn("pr-str")
abstract class PrStrNode(lang: BridjeLanguage) : BuiltInFn(lang) {
    @field:Child
    private var objLib = InteropLibrary.getFactory().createDispatched(3)

    @Specialization
    fun doExecute(obj: Any): String {
        val objView =
            if (!objLib.hasLanguage(obj) || objLib.getLanguage(obj) != BridjeLanguage::class.java) {
                LANG_REF.get(this).getLanguageView(CTX_REF.get(this), obj)
            } else obj

        return expectString(objLib.toDisplayString(objView))
    }
}