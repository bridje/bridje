package brj.emitter

import brj.BridjeContext
import brj.Emitter
import brj.Loc
import brj.analyser.DefMacroExpr
import brj.analyser.ValueExpr
import brj.runtime.*
import brj.runtime.QSymbol.Companion.mkQSym
import brj.types.FnType
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.RootNode
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

@ExportLibrary(InteropLibrary::class)
internal class BridjeFunction(val rootNode: RootNode, val lexObj: Any? = null) : BridjeObject {
    internal val callTarget = Truffle.getRuntime().createCallTarget(rootNode)

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun execute(args: Array<*>): Any? {
        val argsWithScope = arrayOfNulls<Any>(args.size + 1)
        System.arraycopy(args, 0, argsWithScope, 1, args.size)
        argsWithScope[0] = lexObj
        return callTarget.call(*argsWithScope)
    }
}

internal class JavaImportEmitter(private val ctx: BridjeContext) {
    internal inner class JavaExecuteNode(javaImport: JavaImport) : ValueNode() {
        override val loc: Loc? = null

        val fn = InteropLibrary.getFactory().uncached.readMember(ctx.truffleEnv.asHostSymbol(javaImport.clazz.java), javaImport.name)!!

        @Child
        var interop = InteropLibrary.getFactory().create(fn)!!

        @Children
        val argNodes = ((javaImport.type.monoType as FnType).paramTypes.indices).map { ReadArgNode(it + 1) }.toTypedArray()

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
    override fun emitVariantKey(variantKey: VariantKey) = VariantEmitter.emitVariantKey(variantKey)

    override fun emitEffectFn(sym: QSymbol, defaultImpl: BridjeFunction?) =
        BridjeFunction(ctx.makeRootNode(EffectFnBodyNode(sym, defaultImpl)))

    override fun emitDefMacroVar(expr: DefMacroExpr, ns: Symbol): DefMacroVar =
        DefMacroVar(ctx.truffleEnv, mkQSym(ns, expr.sym), expr.type, evalValueExpr(expr.expr) as BridjeFunction)
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
