package brj

import brj.BridjeTypesGen.*
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.ControlFlowException
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.profiles.ConditionProfile
import org.pcollections.HashTreePSet
import org.pcollections.TreePVector
import java.math.BigDecimal
import java.math.BigInteger

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Double::class, BigInteger::class, BigDecimal::class,
    CallTarget::class, DataObject::class)
internal abstract class BridjeTypes

internal class BoolNode(val boolean: Boolean) : ValueNode() {
    override fun execute(frame: VirtualFrame): Boolean = boolean
}

internal class IntNode(val int: Long) : ValueNode() {
    override fun execute(frame: VirtualFrame): Long = int
}

internal class FloatNode(val float: Double) : ValueNode() {
    override fun execute(frame: VirtualFrame): Double = float
}

internal class ObjectNode(val obj: Any) : ValueNode() {
    override fun execute(frame: VirtualFrame): Any = obj
}

data class BigInt(val bigInt: BigInteger) : TruffleObject {
    override fun getForeignAccess() =
        ForeignAccess.create(BigInt::class.java, object : ForeignAccess.StandardFactory {
            override fun accessIsBoxed() = constantly(true)
            override fun accessUnbox() = constantly(bigInt.toLong())
        })

    override fun toString() = bigInt.toString()
}

data class BigFloat(val bigFloat: BigDecimal) : TruffleObject {
    override fun getForeignAccess() =
        ForeignAccess.create(BigInt::class.java, object : ForeignAccess.StandardFactory {
            override fun accessIsBoxed() = constantly(true)
            override fun accessUnbox() = constantly(bigFloat.toDouble())
        })!!

    override fun toString() = bigFloat.toString()
}

internal class CollNode(emitter: ValueExprEmitter, exprs: List<ValueExpr>, private val collFn: (List<Any?>) -> Any) : ValueNode() {
    @Children
    val nodes = exprs.map(emitter::emitValueExpr).toTypedArray()

    @TruffleBoundary
    private fun toColl(list: List<Any?>) = collFn(list)

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val coll: MutableList<Any?> = ArrayList(nodes.size)

        for (node in nodes) {
            coll.add(node.execute(frame))
        }

        return toColl(coll)
    }
}

internal class DoNode(emitter: ValueExprEmitter, expr: DoExpr) : ValueNode() {
    @Children
    val exprNodes = expr.exprs.map(emitter::emitValueExpr).toTypedArray()
    @Child
    var exprNode = emitter.emitValueExpr(expr.expr)

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val exprCount = exprNodes.size
        CompilerAsserts.compilationConstant<Int>(exprCount)

        for (i in 0 until exprCount) {
            exprNodes[i].execute(frame)
        }

        return exprNode.execute(frame)
    }
}

internal class IfNode(emitter: ValueExprEmitter, expr: IfExpr) : ValueNode() {
    @Child
    var predNode = emitter.emitValueExpr(expr.predExpr)
    @Child
    var thenNode = emitter.emitValueExpr(expr.thenExpr)
    @Child
    var elseNode = emitter.emitValueExpr(expr.elseExpr)

    private val conditionProfile = ConditionProfile.createBinaryProfile()

    override fun execute(frame: VirtualFrame): Any =
        (if (conditionProfile.profile(expectBoolean(predNode.execute(frame)))) thenNode else elseNode).execute(frame)
}

internal class LetNode(emitter: ValueExprEmitter, expr: LetExpr) : ValueNode() {
    @Children
    val bindingNodes = expr.bindings
        .map { WriteLocalVarNodeGen.create(emitter.emitValueExpr(it.expr), emitter.frameDescriptor.findOrAddFrameSlot(it.localVar)) }
        .toTypedArray()

    @Child
    var bodyNode: ValueNode = emitter.emitValueExpr(expr.expr)

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val bindingCount = bindingNodes.size
        CompilerAsserts.compilationConstant<Int>(bindingCount)

        for (node in bindingNodes) {
            node.execute(frame)
        }

        return bodyNode.execute(frame)
    }
}

internal class FnBodyNode(emitter: ValueExprEmitter, expr: FnExpr) : ValueNode() {
    @Children
    val readArgNodes = expr.params
        .mapIndexed { idx, it -> WriteLocalVarNodeGen.create(ReadArgNode(idx), emitter.frameDescriptor.findOrAddFrameSlot(it)) }
        .toTypedArray()

    @Child
    var bodyNode: ValueNode = emitter.emitValueExpr(expr.expr)

    override fun execute(frame: VirtualFrame): Any {
        for (node in readArgNodes) {
            node.execute(frame)
        }

        return bodyNode.execute(frame)
    }
}

internal class CallNode(@Child var fnNode: ValueNode, @Children val argNodes: Array<ValueNode>) : ValueNode() {
    @Child
    var callNode = Truffle.getRuntime().createIndirectCallNode()

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val fn = expectCallTarget(fnNode.execute(frame))

        val argValues = arrayOfNulls<Any>(argNodes.size)

        for (i in argNodes.indices) {
            argValues[i] = argNodes[i].execute(frame)
        }

        return callNode.call(fn, argValues)
    }
}

internal class CaseMatched(val res: Any) : ControlFlowException()

internal class CaseClauseNode(emitter: ValueExprEmitter, dataSlot: FrameSlot, clause: CaseClause) : Node() {

    @Child
    var readSlot = ReadLocalVarNodeGen.create(dataSlot)!!

    @Children
    val writeBindingNodes =
        clause.bindings
            ?.mapIndexed { idx, lv ->
                WriteLocalVarNodeGen.create(
                    ReadDataTypeParamNode(ReadLocalVarNodeGen.create(dataSlot), idx),
                    emitter.frameDescriptor.findOrAddFrameSlot(lv))
            }
            ?.toTypedArray()
            ?: arrayOf()

    @Child
    var exprNode = emitter.emitValueExpr(clause.bodyExpr)

    private val conditionProfile = ConditionProfile.createBinaryProfile()!!
    private val constructorSym = clause.constructor.sym

    @ExplodeLoop
    fun execute(frame: VirtualFrame) {
        val value = expectDataObject(readSlot.execute(frame))

        if (conditionProfile.profile(value.constructor.sym == constructorSym)) {
            for (node in writeBindingNodes) {
                node.execute(frame)
            }

            throw CaseMatched(exprNode.execute(frame))
        }
    }
}

internal class CaseExprNode(emitter: ValueExprEmitter, expr: CaseExpr) : ValueNode() {
    private val dataSlot: FrameSlot = emitter.frameDescriptor.findOrAddFrameSlot(this)

    @Child
    var exprNode = WriteLocalVarNodeGen.create(emitter.emitValueExpr(expr.expr), dataSlot)!!

    @Children
    val clauseNodes = expr.clauses.map { CaseClauseNode(emitter, dataSlot, it) }.toTypedArray()

    @Child
    var defaultNode = expr.defaultExpr?.let(emitter::emitValueExpr)

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        exprNode.execute(frame)

        try {
            for (node in clauseNodes) {
                node.execute(frame)
            }
        } catch (e: CaseMatched) {
            return e.res
        }

        return defaultNode?.execute(frame) ?: TODO()
    }
}


internal class ValueExprEmitter(lang: BrjLanguage) : TruffleEmitter(lang) {

    @Deprecated("shouldn't be necessary once we have full interop")
    inner class WrapGuestValueNode(@Child var node: ValueNode) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any {
            val res = node.execute(frame)

            return when (res) {
                is Boolean, is Long, is String, is TruffleObject -> res
                else -> lang.contextReference.get().truffleEnv.asGuestValue(res)
            }
        }
    }

    fun emitValueExpr(expr: ValueExpr): ValueNode =
        when (expr) {
            is BooleanExpr -> BoolNode(expr.boolean)
            is StringExpr -> ObjectNode(expr.string)
            is IntExpr -> IntNode(expr.int)
            is BigIntExpr -> ObjectNode(BigInt(expr.bigInt))
            is FloatExpr -> FloatNode(expr.float)
            is BigFloatExpr -> ObjectNode(BigFloat(expr.bigFloat))

            is VectorExpr -> CollNode(this, expr.exprs) { TreePVector.from(it) }

            is SetExpr -> CollNode(this, expr.exprs) { HashTreePSet.from(it) }

            is FnExpr -> {
                val emitter = ValueExprEmitter(lang)
                ObjectNode(emitter.makeCallTarget(FnBodyNode(emitter, expr)))
            }

            is CallExpr -> CallNode(emitValueExpr(expr.f), expr.args.map(::emitValueExpr).toTypedArray())

            is IfExpr -> IfNode(this, expr)
            is DoExpr -> DoNode(this, expr)
            is LetExpr -> LetNode(this, expr)

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.localVar))
            is GlobalVarExpr -> ObjectNode(expr.globalVar.value!!)

            is CaseExpr -> CaseExprNode(this, expr)
        }
}

internal fun emitValueExpr(lang: BrjLanguage, expr: ValueExpr): CallTarget {
    val emitter = ValueExprEmitter(lang)
    return emitter.makeCallTarget(emitter.WrapGuestValueNode(emitter.emitValueExpr(expr)))
}

internal fun evalValueExpr(lang: BrjLanguage, expr: ValueExpr): Any {
    val emitter = ValueExprEmitter(lang)
    return emitter.emitValueExpr(expr).execute(Truffle.getRuntime().createVirtualFrame(emptyArray(), emitter.frameDescriptor)!!)
}
