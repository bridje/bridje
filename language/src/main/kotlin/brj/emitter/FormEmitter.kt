package brj.emitter

import brj.BridjeContext
import brj.BridjeLanguage
import brj.reader.*
import brj.runtime.BridjeFunction
import brj.runtime.NSEnv
import brj.runtime.VariantKeyVar
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame

internal abstract class FormNode(val constructor: (Any) -> Form) : ValueNode() {

    @TruffleBoundary
    private fun construct(arg: Any) = constructor(arg)

    @Suppress("UNCHECKED_CAST")
    @Specialization
    fun doExecute(frame: VirtualFrame, @CachedContext(BridjeLanguage::class) ctx: BridjeContext): Form {
        val truffleEnv = ctx.truffleEnv
        return construct(frame.arguments[1].let { if (truffleEnv.isHostObject(it)) truffleEnv.asHostObject(it) else it })
    }
}

internal fun formsNSEnv(ctx: BridjeContext) =
    NSEnv(FORM_NS,
        typeAliases = mapOf(FORM.local to FORM_TYPE_ALIAS),
        vars = FORM_TYPES.values.associate {
            it.variantKey.sym.local to VariantKeyVar(it.variantKey, BridjeFunction(ctx.language.BridjeRootNode(FormNodeGen.create(it.constructor as (Any) -> Form))))
        })
