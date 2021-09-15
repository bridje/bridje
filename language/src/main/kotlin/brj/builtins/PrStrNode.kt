package brj.builtins

import brj.BridjeLanguage
import brj.BridjeTypesGen.expectString
import brj.runtime.BridjeContext
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.CachedLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary

@BuiltIn("pr-str")
abstract class PrStrNode(lang: BridjeLanguage) : BuiltInFn(lang) {
    @field:Child
    private var objLib = InteropLibrary.getFactory().createDispatched(3)

    @Specialization
    fun doExecute(
        obj: Any,
        @CachedLanguage lang: BridjeLanguage,
        @CachedContext(BridjeLanguage::class) ctx: BridjeContext
    ): String {
        val objView =
            if (!objLib.hasLanguage(obj) || objLib.getLanguage(obj) != BridjeLanguage::class.java) {
                lang.getLanguageView(ctx, obj)
            } else obj

        return expectString(objLib.toDisplayString(objView))
    }
}