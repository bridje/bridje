package brj.emitter

import brj.BridjeContext
import brj.Emitter
import brj.analyser.DefMacroExpr
import brj.analyser.ValueExpr
import brj.emitter.JavaImportEmitterFactory.JavaExecuteNodeGen
import brj.emitter.JavaImportEmitterFactory.JavaInstantiateNodeGen
import brj.reader.Loc
import brj.runtime.*
import brj.types.FnType
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.profiles.ConditionProfile
import java.math.BigDecimal
import java.math.BigInteger

internal data class ReadArgNode(val idx: Int) : ValueNode() {
    override val loc: Loc? = null
    override fun execute(frame: VirtualFrame) = frame.arguments[idx]!!
}

@NodeField(name = "slot", type = FrameSlot::class)
internal abstract class ReadLocalVarNode : ValueNode() {
    override val loc: Loc? = null
    abstract fun getSlot(): FrameSlot

    @Specialization
    fun readObject(frame: VirtualFrame): Any = FrameUtil.getObjectSafe(frame, getSlot()) ?: TODO()
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

internal class ArrayNode<N : ValueNode>(@Children val valueNodes: Array<N>) : ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Array<Any?> {
        val res = arrayOfNulls<Any>(valueNodes.size)

        for (i in valueNodes.indices) {
            res[i] = valueNodes[i].execute(frame)
        }

        return res
    }
}


internal class JavaImportEmitter(private val ctx: BridjeContext) {

    @NodeChild("args", type = ArrayNode::class)
    @NodeField(name = "fn", type = Any::class)
    internal abstract class JavaExecuteNode : ValueNode() {
        override val loc: Loc? = null

        @Specialization(guards = ["interop.isExecutable(fn)"])
        fun doExecute(fn: Any, args: Array<*>, @CachedLibrary("fn") interop: InteropLibrary): Any = interop.execute(fn, *args)
    }

    @NodeChild("args", type = ArrayNode::class)
    @NodeField(name = "clazzObj", type = Any::class)
    internal abstract class JavaInstantiateNode : ValueNode() {
        override val loc: Loc? = null

        abstract fun getClazzObj(): Any

        @Specialization(guards = ["interop.isInstantiable(clazzObj)"])
        fun doExecute(args: Array<*>, @CachedLibrary("clazzObj") interop: InteropLibrary): Any = interop.instantiate(getClazzObj(), *args)
    }

    fun emitJavaImport(clazz: Symbol, name: String, paramCount: Int): BridjeFunction {
        if (!ctx.truffleEnv.isHostLookupAllowed) TODO("host lookup not allowed")

        val argNodes = ArrayNode((0 until paramCount).map { ReadArgNode(it + 1) }.toTypedArray())
        val clazzObj =
            kotlin.runCatching { Class.forName(clazz.baseStr) }.map { ctx.truffleEnv.asHostSymbol(it) }.getOrDefault(null)
                ?: ctx.truffleEnv.lookupHostSymbol(clazz.baseStr)!!
        val interop = InteropLibrary.getFactory().uncached!!

        val node =
            if (name == "new") JavaInstantiateNodeGen.create(argNodes, clazzObj)
            else JavaExecuteNodeGen.create(argNodes, interop.readMember(clazzObj, name))

        return BridjeFunction(ctx.language.BridjeRootNode(node))
    }

    fun emitJavaImport(javaImport: JavaImport) =
        emitJavaImport(javaImport.clazz, javaImport.name, (javaImport.type.monoType as FnType).paramTypes.size)
}

internal class EffectFnBodyNode(val sym: QSymbol, defaultImpl: BridjeFunction?) : ValueNode() {
    override val loc: Loc? = null

    @Child
    var indirectCallNode = Truffle.getRuntime().createIndirectCallNode()!!

    @Child
    var directCallNode = if (defaultImpl != null) Truffle.getRuntime().createDirectCallNode(defaultImpl.callTarget) else null

    private val conditionProfile: ConditionProfile = ConditionProfile.createBinaryProfile()

    override fun execute(frame: VirtualFrame): Any {
        @Suppress("UNCHECKED_CAST") val f = (frame.arguments[0] as FxMap)[sym]

        return if (conditionProfile.profile(f == null)) {
            directCallNode!!.call(*frame.arguments)
        } else {
            f!! // we know that we only hit the else if `f!!`, but the compiler doesn't
            frame.arguments[0] = f.lexObj
            indirectCallNode.call(f.callTarget, *frame.arguments)
        }
    }
}

internal class TruffleEmitter(private val ctx: BridjeContext) : Emitter {
    override fun evalValueExpr(expr: ValueExpr) = ValueExprEmitter(ctx).evalValueExpr(expr)
    override fun emitJavaImport(javaImport: JavaImport) = JavaImportEmitter(ctx).emitJavaImport(javaImport)
    override fun emitRecordKey(recordKey: RecordKey) = RecordEmitter(ctx).emitRecordKey(recordKey)
    override fun emitVariantKey(variantKey: VariantKey) = VariantEmitter(ctx).emitVariantKey(variantKey)

    override fun emitEffectFn(sym: QSymbol, defaultImpl: BridjeFunction?) =
        BridjeFunction(ctx.language.BridjeRootNode(EffectFnBodyNode(sym, defaultImpl)))

    override fun emitDefMacroVar(expr: DefMacroExpr, ns: Symbol): DefMacroVar =
        DefMacroVar(ctx.truffleEnv, QSymbol(ns, expr.sym), expr.type, evalValueExpr(expr.expr) as BridjeFunction)
}

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Double::class,
    BigInteger::class, BigDecimal::class,
    BridjeFunction::class, RecordObject::class, VariantObject::class,
    Symbol::class, QSymbol::class,
    VirtualFrame::class)
internal abstract class BridjeTypes

internal interface BridjeObject : TruffleObject

@TypeSystemReference(BridjeTypes::class)
@NodeInfo(language = "brj")
internal abstract class ValueNode : Node() {
    open val loc: Loc? = null

    override fun getSourceSection() = loc

    abstract fun execute(frame: VirtualFrame): Any?
}
