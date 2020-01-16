package brj.emitter

import brj.BridjeContext
import brj.reader.*
import brj.runtime.BridjeFunction
import brj.runtime.NSEnv
import brj.runtime.VariantKeyVar
import com.oracle.truffle.api.CompilerDirectives.*
import com.oracle.truffle.api.frame.VirtualFrame

internal class FormNode<I>(val constructor: (I) -> Form): ValueNode() {

    @TruffleBoundary
    private fun construct(i: I) = constructor(i)

    @Suppress("UNCHECKED_CAST")
    override fun execute(frame: VirtualFrame) = construct(frame.arguments[1] as I)
}

internal fun formsNSEnv(ctx: BridjeContext) =
    NSEnv(FORM_NS,
        typeAliases = mapOf(FORM.local to FORM_TYPE_ALIAS),
        vars = FORM_TYPES.values.associate {
            it.variantKey.sym.local to VariantKeyVar(it.variantKey, BridjeFunction(ctx.language.BridjeRootNode(FormNode(it.constructor))))
        })
