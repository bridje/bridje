package brj

import brj.BridjeTypesGen.expectDataObject
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.Layout
import com.oracle.truffle.api.`object`.ObjectType
import com.oracle.truffle.api.`object`.Property
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

internal val LAYOUT = Layout.createLayout()

object DataObjectType : ObjectType()

class DataObject(val constructor: DataTypeConstructor, val dynamicObject: DynamicObject, private val faf: ForeignAccess.StandardFactory) : TruffleObject {
    override fun getForeignAccess() = ForeignAccess.create(DataObject::class.java, faf)!!

    @TruffleBoundary
    override fun toString(): String =
        if (constructor.paramTypes == null)
            constructor.sym.toString()
        else
            "(${constructor.sym} ${constructor.paramTypes.mapIndexed { idx, _ -> dynamicObject[idx] }.joinToString(" ")})"
}

internal class ReadDataTypeParamNode(@Child var objNode: ValueNode, val idx: Int) : ValueNode() {
    override fun execute(frame: VirtualFrame): Any {
        return expectDataObject(objNode.execute(frame)).dynamicObject[idx]
    }
}

internal class DataTypeInteropReadNode(constructor: DataTypeConstructor) : ValueNode() {
    private val paramCount = constructor.paramTypes?.size

    override fun execute(frame: VirtualFrame): Any {
        val obj = frame.arguments[0] as DataObject
        val idxArg = frame.arguments[1]
        return when (idxArg) {
            is Long -> {
                val idx = idxArg.toInt()
                if (paramCount == null || idx >= paramCount) throw IndexOutOfBoundsException(idx)

                obj.dynamicObject[idx]
            }

            "constructor" -> obj.constructor.sym.toString()
            "dataType" -> obj.constructor.dataType.sym.toString()

            else -> TODO()
        }
    }
}

typealias DataObjectFactory = (Array<Any?>) -> DataObject

internal fun objectFactory(emitter: TruffleEmitter, constructor: DataTypeConstructor): DataObjectFactory {
    val allocator = LAYOUT.createAllocator()
    var shape = LAYOUT.createShape(DataObjectType)

    constructor.paramTypes?.forEachIndexed { idx, paramType ->
        shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
    }

    val factory = shape.createFactory()

    return { args ->
        DataObject(constructor, factory.newInstance(*args), object : ForeignAccess.StandardFactory {
            private fun constantly(obj: Any) = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(obj))

            override fun accessHasSize(): CallTarget = constantly(true)
            override fun accessGetSize(): CallTarget? = constructor.paramTypes?.let { constantly(it.size) }
            override fun accessHasKeys(): CallTarget = constantly(true)
            override fun accessRead(): CallTarget = DataTypeEmitter(emitter.lang).makeCallTarget(DataTypeInteropReadNode(constructor))
        })
    }
}

internal class FunctionConstructorNode(private val dataObjectFactory: DataObjectFactory, private val paramTypes: List<MonoType>) : ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(paramTypes.size)

        for (i in paramTypes.indices) {
            params[i] = frame.arguments[i]
        }

        return dataObjectFactory(params)
    }
}

internal class DataTypeEmitter(lang: BrjLanguage) : TruffleEmitter(lang) {
    fun emitConstructor(constructor: DataTypeConstructor): ConstructorVar {
        val dataObjectFactory = objectFactory(this, constructor)

        return ConstructorVar(constructor,
            if (constructor.paramTypes != null)
                BridjeFunction(this, FunctionConstructorNode(dataObjectFactory, constructor.paramTypes))
            else
                dataObjectFactory(emptyArray()))
    }
}

internal fun emitConstructor(lang: BrjLanguage, constructor: DataTypeConstructor) = DataTypeEmitter(lang).emitConstructor(constructor)
