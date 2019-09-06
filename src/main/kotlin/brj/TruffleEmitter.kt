package brj

import brj.BridjeTypesGen.expectRecordObject
import brj.BridjeTypesGen.expectVariantObject
import brj.Symbol.Companion.mkSym
import brj.analyser.DefMacroExpr
import brj.analyser.ValueExpr
import brj.types.FnType
import brj.types.MonoType
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.Layout
import com.oracle.truffle.api.`object`.ObjectType
import com.oracle.truffle.api.`object`.Property
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import org.graalvm.polyglot.Value

internal class ReadArgNode(val idx: Int) : ValueNode(loc = null) {
    override fun execute(frame: VirtualFrame) = frame.arguments[idx]!!
}

@NodeField(name = "slot", type = FrameSlot::class)
internal abstract class ReadLocalVarNode : ValueNode(loc = null) {
    abstract fun getSlot(): FrameSlot

    @Specialization
    fun readObject(frame: VirtualFrame): Any = FrameUtil.getObjectSafe(frame, getSlot())!!
}

@NodeChild("value", type = ValueNode::class)
@NodeField(name = "slot", type = FrameSlot::class)
internal abstract class WriteLocalVarNode : Node() {
    abstract fun getSlot(): FrameSlot

    @Specialization
    fun writeObject(frame: VirtualFrame, value: Any) {
        frame.setObject(getSlot(), value)
    }

    abstract fun execute(frame: VirtualFrame)
}

@ExportLibrary(InteropLibrary::class)
internal class BridjeFunction internal constructor(rootNode: RootNode) : TruffleObject {
    val callTarget = Truffle.getRuntime().createCallTarget(rootNode)!!

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun execute(args: Array<*>) = callTarget.call(*args)!!
}

@ExportLibrary(InteropLibrary::class)
internal class RecordObject(private val truffleEnv: TruffleLanguage.Env, val keys: List<RecordKey>, val dynamicObject: DynamicObject) : TruffleObject {
    private val keyStrings by lazy {
        keys.associate { it.sym.toString() to it.sym }
    }

    @TruffleBoundary
    override fun toString(): String = "{${keys.joinToString(", ") { key -> "${key.sym} ${dynamicObject[key.sym.toString()]}" }}}"

    @ExportMessage
    fun hasMembers() = true

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    fun getMembers(includeInternal: Boolean) = truffleEnv.asGuestValue(keyStrings.keys.toList())!!

    @ExportMessage
    fun isMemberReadable(name: String) = keyStrings.keys.contains(name)

    @ExportMessage
    fun readMember(name: String) = dynamicObject[name]!!
}

internal class RecordKeyReadNode(val recordKey: RecordKey) : ValueNode(loc = null) {
    @Child
    var readArgNode = ReadArgNode(0)

    override fun execute(frame: VirtualFrame) = expectRecordObject(readArgNode.execute(frame)).dynamicObject[recordKey.sym.toString()]!!
}

internal typealias RecordObjectFactory = (Array<Any?>) -> RecordObject

internal class RecordEmitter(val ctx: BridjeContext) {
    companion object {
        private val LAYOUT = Layout.createLayout()!!
    }

    internal data class RecordObjectType(val keys: Set<RecordKey>) : ObjectType()

    internal fun recordObjectFactory(keys: List<RecordKey>): RecordObjectFactory {
        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(RecordObjectType(keys.toSet()))

        keys.forEach { key ->
            shape = shape.addProperty(Property.create(key.sym.toString(), allocator.locationForType(key.type.javaType), 0))
        }

        val factory = shape.createFactory()

        return { vals ->
            RecordObject(ctx.truffleEnv, keys, factory.newInstance(*vals))
        }
    }

    internal fun emitRecordKey(recordKey: RecordKey) = BridjeFunction(ctx.makeRootNode(RecordKeyReadNode(recordKey)))
}

@ExportLibrary(InteropLibrary::class)
internal class VariantObject(val variantKey: VariantKey, val dynamicObject: DynamicObject) : TruffleObject {
    private val paramCount = variantKey.paramTypes.size

    @TruffleBoundary
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
        if (isArrayElementReadable(idx)) dynamicObject[idx]
        else {
            CompilerDirectives.transferToInterpreter();
            throw IndexOutOfBoundsException(idx.toInt())
        }
}

internal class ReadVariantParamNode(@Child var objNode: ValueNode, val idx: Int) : ValueNode(loc = null) {
    override fun execute(frame: VirtualFrame): Any {
        return expectVariantObject(objNode.execute(frame)).dynamicObject[idx]
    }
}

internal typealias VariantObjectFactory = (Array<Any?>) -> VariantObject

internal class VariantConstructorNode(private val variantObjectFactory: VariantObjectFactory, private val paramTypes: List<MonoType>) : ValueNode(loc = null) {
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
            BridjeFunction(ctx.makeRootNode(VariantConstructorNode(variantObjectFactory, variantKey.paramTypes)))
        else
            variantObjectFactory(emptyArray())

    }
}

internal class JavaImportEmitter(private val ctx: BridjeContext) {
    internal inner class JavaExecuteNode(javaImport: JavaImport) : ValueNode(loc = null) {
        val fn = InteropLibrary.getFactory().uncached.readMember(ctx.truffleEnv.asHostSymbol(javaImport.clazz), javaImport.name)

        @Child
        var interop = InteropLibrary.getFactory().create(fn)

        @Children
        val argNodes = (0 until (javaImport.type.monoType as FnType).paramTypes.size).map(::ReadArgNode).toTypedArray()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val params = arrayOfNulls<Any>(argNodes.size)

            for (i in argNodes.indices) {
                params[i] = argNodes[i].execute(frame)
            }

            return interop.execute(fn, *params)
        }
    }

    fun emitJavaImport(javaImport: JavaImport): BridjeFunction =
        BridjeFunction(ctx.makeRootNode(JavaExecuteNode(javaImport)))
}

internal typealias FxMap = Map<QSymbol, BridjeFunction>

internal class EffectEmitter(val ctx: BridjeContext) {

    internal class LookupEffectNode(val sym: QSymbol) : Node() {
        @Suppress("UNCHECKED_CAST")
        fun execute(frame: VirtualFrame): BridjeFunction? =
            (frame.arguments[0] as List<FxMap>).first()[sym]
    }

    internal inner class EffectFnBodyNode(val sym: QSymbol, defaultImpl: BridjeFunction?) : ValueNode(loc = null) {
        @Child
        var lookupEffectNode = LookupEffectNode(sym)

        val defaultCallTarget =
            if (defaultImpl != null) defaultImpl.callTarget
            else Truffle.getRuntime().createCallTarget(ctx.makeRootNode(object : ValueNode(loc = null) {
                override fun execute(frame: VirtualFrame): Any = throw IllegalStateException("Can't find effect.")
            }))

        @Child
        var callNode = Truffle.getRuntime().createIndirectCallNode()!!

        @TruffleBoundary
        fun transformArgs(args: Array<Any?>): Array<Any?> {
            val args_ = args.clone()

            @Suppress("UNCHECKED_CAST") val fx = (args[0] as List<FxMap>)

            args_[0] = listOf(fx[0] + (sym to fx[1][sym])) + fx.drop(1)

            return args_
        }

        override fun execute(frame: VirtualFrame): Any {
            return callNode.call(lookupEffectNode.execute(frame)?.callTarget
                ?: defaultCallTarget, transformArgs(frame.arguments))
        }
    }

    fun emitEffectExpr(sym: QSymbol, defaultImpl: BridjeFunction?) =
        BridjeFunction(ctx.makeRootNode(EffectFnBodyNode(sym, defaultImpl)))
}

internal class TruffleEmitter(val ctx: BridjeContext) : Emitter {
    override fun evalValueExpr(expr: ValueExpr) = ValueExprEmitter(ctx).evalValueExpr(expr)
    override fun emitJavaImport(javaImport: JavaImport) = JavaImportEmitter(ctx).emitJavaImport(javaImport)
    override fun emitRecordKey(recordKey: RecordKey) = RecordEmitter(ctx).emitRecordKey(recordKey)
    override fun emitVariantKey(variantKey: VariantKey) = VariantEmitter(ctx).emitVariantKey(variantKey)
    override fun evalEffectExpr(sym: QSymbol, defaultImpl: BridjeFunction?) = EffectEmitter(ctx).emitEffectExpr(sym, defaultImpl)
    override fun emitDefMacroVar(expr: DefMacroExpr): DefMacroVar =
        // HACK where should I be getting the brj.forms NSEnv from?
        DefMacroVar(ctx.truffleEnv, expr.sym, expr.type, ctx.env.nses[mkSym("brj.forms")]!!, evalValueExpr(expr.expr))
}

