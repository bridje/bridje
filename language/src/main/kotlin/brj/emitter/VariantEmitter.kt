package brj.emitter

import brj.BridjeLanguage.Companion.currentBridjeContext
import brj.Loc
import brj.runtime.BridjeFunction
import brj.runtime.VariantKey
import brj.runtime.VariantObject
import brj.types.MonoType
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop

internal data class ReadVariantParamNode(@Child var objNode: ValueNode, val idx: Int) : ValueNode() {
    override val loc: Loc? = null

    override fun execute(frame: VirtualFrame): Any? {
        return BridjeTypesGen.expectVariantObject(objNode.execute(frame)).args[idx]
    }
}

internal data class VariantConstructorNode(private val variantKey: VariantKey, private val paramTypes: List<MonoType>) : ValueNode() {
    override val loc: Loc? = null

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(paramTypes.size)

        for (i in paramTypes.indices) {
            params[i] = frame.arguments[i + 1]
        }

        return VariantObject(variantKey, params)
    }
}

internal object VariantEmitter {
    fun emitVariantKey(variantKey: VariantKey) =
        if (variantKey.paramTypes.isNotEmpty())
            BridjeFunction(currentBridjeContext().makeRootNode(VariantConstructorNode(variantKey, variantKey.paramTypes)))
        else
            VariantObject(variantKey, emptyArray())
}
