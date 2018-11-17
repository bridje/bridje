package brj

import brj.BridjeTypesGen.expectDynamicObject
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.`object`.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

internal val LAYOUT = Layout.createLayout()

internal class ReadDataTypeParamNode(@Child var objNode: ValueNode, val idx: Int) : ValueNode() {
    override fun execute(frame: VirtualFrame): Any {
        return expectDynamicObject(objNode.execute(frame))[idx]
    }
}

internal class FunctionConstructorNode(private val factory: DynamicObjectFactory, private val paramTypes: List<MonoType>) : ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(paramTypes.size)

        for (i in paramTypes.indices) {
            params[i] = frame.arguments[i]
        }

        return factory.newInstance(*params)
    }
}

internal class DataTypeInteropReadNode(val constructor: DataTypeConstructor) : ValueNode() {
    private val paramCount = constructor.paramTypes?.size

    override fun execute(frame: VirtualFrame): Any {
        val obj = frame.arguments[0] as DynamicObject
        val idxArg = frame.arguments[1]

        return when (idxArg) {
            is Long -> {
                val idx = idxArg.toInt()
                if (paramCount == null || idx >= paramCount) throw IndexOutOfBoundsException(idx)

                obj[idx]
            }

            "constructor" -> constructor.sym.toString()
            "dataType" -> constructor.dataType.sym.toString()

            else -> TODO()
        }
    }
}

internal class DataTypeEmitter(lang: BrjLanguage) : TruffleEmitter(lang) {
    inner class DataTypeForeignAccessFactory(val constructor: DataTypeConstructor) : ForeignAccess.StandardFactory {
        private fun constantly(obj: Any) = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(obj))

        override fun accessHasSize(): CallTarget = constantly(true)
        override fun accessGetSize(): CallTarget? = constructor.paramTypes?.let { constantly(it.size) }
        override fun accessHasKeys(): CallTarget = constantly(true)
        override fun accessRead(): CallTarget = Truffle.getRuntime().createCallTarget(RootValueNode(DataTypeInteropReadNode(constructor)))
    }

    inner class ConstructorType(val constructor: DataTypeConstructor) : ObjectType() {
        @TruffleBoundary
        override fun toString(obj: DynamicObject): String =
            if (constructor.paramTypes == null)
                constructor.sym.toString()
            else
                "(${constructor.sym} ${constructor.paramTypes.mapIndexed { idx, _ -> obj[idx] }.joinToString(" ")})"

        override fun getForeignAccessFactory(obj: DynamicObject): ForeignAccess =
            ForeignAccess.create(obj.javaClass, DataTypeForeignAccessFactory(constructor))
    }

    fun dynamicObjectFactory(constructor: DataTypeConstructor): DynamicObjectFactory {
        val constructorType = ConstructorType(constructor)

        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(constructorType)

        constructor.paramTypes?.forEachIndexed { idx, paramType ->
            shape = shape.addProperty(Property.create(idx, allocator.locationForType(paramType.javaType), 0))
        }

        return shape.createFactory()
    }

    fun emitConstructor(constructor: DataTypeConstructor): ConstructorVar {
        val factory = dynamicObjectFactory(constructor)

        return ConstructorVar(constructor,
            if (constructor.paramTypes != null)
                makeCallTarget(FunctionConstructorNode(factory, constructor.paramTypes))
            else
                factory.newInstance())
    }
}

internal fun emitConstructor(lang: BrjLanguage, constructor: DataTypeConstructor) = DataTypeEmitter(lang).emitConstructor(constructor)
