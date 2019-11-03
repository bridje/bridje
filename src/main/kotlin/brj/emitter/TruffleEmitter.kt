package brj.emitter

import brj.BridjeContext
import brj.Emitter
import brj.Loc
import brj.analyser.DefMacroExpr
import brj.analyser.Resolver
import brj.analyser.ValueExpr
import brj.emitter.ValueExprEmitter.BridjeFunction
import brj.runtime.*
import brj.runtime.QSymbol.Companion.mkQSym
import brj.types.FnType
import brj.types.PolyConstraint
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameUtil
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
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

internal class ArrayNode(@Children val valueNodes: Array<ValueNode>): ValueNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Array<*> {
        val res = arrayOfNulls<Any>(valueNodes.size)

        for (i in valueNodes.indices) {
            res[i] = valueNodes[i].execute(frame)
        }

        return res
    }
}

internal class JavaImportEmitter(private val ctx: BridjeContext) {
    internal inner class JavaExecuteNode(javaImport: JavaImport) : ValueNode() {
        override val loc: Loc? = null

        val fn = InteropLibrary.getFactory().uncached.readMember(ctx.truffleEnv.asHostSymbol(javaImport.clazz.java), javaImport.name)!!

        @Child
        var interop = InteropLibrary.getFactory().create(fn)!!

        @Children
        val argNodes = ((javaImport.type.monoType as FnType).paramTypes.indices).map(::ReadArgNode).toTypedArray()

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

internal class EffectFnBodyNode(val sym: QSymbol) : ValueNode() {
    override val loc: Loc? = null

    @Child
    var callNode = Truffle.getRuntime().createIndirectCallNode()!!

    @Suppress("UNCHECKED_CAST")
    override fun execute(frame: VirtualFrame): Any =
        callNode.call((frame.arguments[0] as FxMap)[sym]!!.callTarget, *frame.arguments)
}

internal class TruffleEmitter(private val ctx: BridjeContext, private val formsResolver: Resolver) : Emitter {
    override fun evalValueExpr(expr: ValueExpr) = ValueExprEmitter(ctx).evalValueExpr(expr)
    override fun emitJavaImport(javaImport: JavaImport) = JavaImportEmitter(ctx).emitJavaImport(javaImport)
    override fun emitRecordKey(recordKey: RecordKey) = RecordEmitter(ctx).emitRecordKey(recordKey)
    override fun emitVariantKey(variantKey: VariantKey) = VariantEmitter(ctx).emitVariantKey(variantKey)
    override fun emitEffectFn(sym: QSymbol) = BridjeFunction(ctx.makeRootNode(EffectFnBodyNode(sym)))

    override fun emitDefMacroVar(expr: DefMacroExpr, ns: Symbol): DefMacroVar =
        DefMacroVar(ctx.truffleEnv, mkQSym(ns, expr.sym), expr.type, formsResolver, evalValueExpr(expr.expr))

    override fun emitPolyVar(polyConstraint: PolyConstraint) = ValueExprEmitter(ctx).emitPolyVar()
}

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Double::class,
    BigInteger::class, BigDecimal::class,
    BridjeFunction::class, RecordObject::class, VariantObject::class,
    Symbol::class, QSymbol::class)
internal abstract class BridjeTypes

internal interface BridjeObject : TruffleObject

@TypeSystemReference(BridjeTypes::class)
@NodeInfo(language = "bridje")
internal abstract class ValueNode : Node() {
    open val loc: Loc? = null
    abstract fun execute(frame: VirtualFrame): Any

    override fun getSourceSection() = loc
}


