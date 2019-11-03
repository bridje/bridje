package brj.emitter

import brj.BridjeContext
import brj.Loc
import brj.runtime.VariantKey
import brj.types.MonoType
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.Layout
import com.oracle.truffle.api.`object`.ObjectType
import com.oracle.truffle.api.`object`.Property
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop
import org.graalvm.polyglot.Value

@ExportLibrary(InteropLibrary::class)
internal class VariantObject(val variantKey: VariantKey, val dynamicObject: DynamicObject) : BridjeObject {
    private val paramCount = variantKey.paramTypes.size

    @CompilerDirectives.TruffleBoundary
    override fun toString(): String =
        if (variantKey.paramTypes.isEmpty())
            variantKey.sym.toString()
        else
            "(${variantKey.sym} ${variantKey.paramTypes
                .mapIndexed { idx, _ ->
                    val el = Value.asValue(dynamicObject[idx])
                    if (el.isHostObject) el.asHostObject<Any>().toString() else el.toString()
                }
                .joinToString(" ")})"

    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = paramCount

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx < paramCount

    @ExportMessage
    fun readArrayElement(idx: Long) =
        if (isArrayElementReadable(idx)) dynamicObject[idx]!!
        else {
            CompilerDirectives.transferToInterpreter()
            throw IndexOutOfBoundsException(idx.toInt())
        }
}

internal data class ReadVariantParamNode(@Child var objNode: ValueNode, val idx: Int) : ValueNode() {
    override val loc: Loc? = null

    override fun execute(frame: VirtualFrame): Any {
        return BridjeTypesGen.expectVariantObject(objNode.execute(frame)).dynamicObject[idx]
    }
}

internal typealias VariantObjectFactory = (Array<Any?>) -> VariantObject

internal data class VariantConstructorNode(private val variantObjectFactory: VariantObjectFactory, private val paramTypes: List<MonoType>) : ValueNode() {
    override val loc: Loc? = null

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(paramTypes.size)

        for (i in paramTypes.indices) {
            params[i] = frame.arguments[i]
        }

        return variantObjectFactory(params)
    }
}

internal class VariantEmitter(val ctx: BridjeContext) {
    private val LAYOUT = Layout.createLayout()

    internal data class VariantObjectType(val variantKey: VariantKey) : ObjectType()

    private fun objectFactory(variantKey: VariantKey): VariantObjectFactory {
        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(VariantObjectType(variantKey))

        variantKey.paramTypes.forEachIndexed { idx, paramType ->
            shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
        }

        val factory = shape.createFactory()

        return { args -> VariantObject(variantKey, factory.newInstance(*args)) }
    }

    fun emitVariantKey(variantKey: VariantKey): TruffleObject {
        val variantObjectFactory = objectFactory(variantKey)
        return if (variantKey.paramTypes.isNotEmpty())
            ValueExprEmitter.BridjeFunction(ctx.makeRootNode(VariantConstructorNode(variantObjectFactory, variantKey.paramTypes)))
        else
            variantObjectFactory(emptyArray())

    }
}
