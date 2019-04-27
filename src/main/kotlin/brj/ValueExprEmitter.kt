package brj

import brj.BridjeTypesGen.*
import brj.BrjLanguage.Companion.getCtx
import brj.analyser.*
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.ConditionProfile

internal class ValueExprEmitter private constructor() {
    val frameDescriptor = FrameDescriptor()

    class BoolNode(val boolean: Boolean) : ValueNode() {
        override fun execute(frame: VirtualFrame): Boolean = boolean
    }

    class IntNode(val int: Long) : ValueNode() {
        override fun execute(frame: VirtualFrame): Long = int
    }

    class FloatNode(val float: Double) : ValueNode() {
        override fun execute(frame: VirtualFrame): Double = float
    }

    class ObjectNode(val obj: Any) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any = obj
    }

    inner class CollNode(exprs: List<ValueExpr>) : Node() {
        @Children
        val nodes = exprs.map(::emitValueExpr).toTypedArray()

        @TruffleBoundary
        private fun add(coll: MutableCollection<Any>, value: Any) = coll.add(value)

        @ExplodeLoop
        fun execute(frame: VirtualFrame, coll: MutableCollection<Any>) {
            for (node in nodes) {
                add(coll, node.execute(frame))
            }
        }
    }

    inner class VectorNode(expr: VectorExpr) : ValueNode() {
        @Child
        var collNode = CollNode(expr.exprs)

        override fun execute(frame: VirtualFrame): List<Any> {
            val coll = mutableListOf<Any>()
            collNode.execute(frame, coll)
            return coll
        }
    }

    inner class SetNode(expr: SetExpr) : ValueNode() {
        @Child
        var collNode = CollNode(expr.exprs)

        override fun execute(frame: VirtualFrame): Set<Any> {
            val coll = mutableSetOf<Any>()
            collNode.execute(frame, coll)
            return coll
        }
    }

    inner class RecordNode(expr: RecordExpr) : ValueNode() {
        val keys = expr.entries.map(RecordEntry::recordKey)
        val factory = RecordEmitter.recordObjectFactory(keys)

        @Children
        val valNodes = expr.entries.map { emitValueExpr(it.expr) }.toTypedArray()

        @TruffleBoundary
        private fun buildRecord(vals: Array<Any?>) = factory(vals)

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val vals = arrayOfNulls<Any>(valNodes.size)

            for (idx in valNodes.indices) {
                vals[idx] = valNodes[idx].execute(frame)
            }

            return buildRecord(vals)
        }
    }

    inner class DoNode(expr: DoExpr) : ValueNode() {
        @Children
        val exprNodes = expr.exprs.map(::emitValueExpr).toTypedArray()
        @Child
        var exprNode = emitValueExpr(expr.expr)

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

    inner class IfNode(expr: IfExpr) : ValueNode() {
        @Child
        var predNode = emitValueExpr(expr.predExpr)
        @Child
        var thenNode = emitValueExpr(expr.thenExpr)
        @Child
        var elseNode = emitValueExpr(expr.elseExpr)

        private val conditionProfile = ConditionProfile.createBinaryProfile()

        override fun execute(frame: VirtualFrame): Any =
            (if (conditionProfile.profile(expectBoolean(predNode.execute(frame)))) thenNode else elseNode).execute(frame)
    }

    inner class LetNode(expr: LetExpr) : ValueNode() {
        @Children
        val bindingNodes = expr.bindings
            .map { WriteLocalVarNodeGen.create(emitValueExpr(it.expr), frameDescriptor.findOrAddFrameSlot(it.localVar)) }
            .toTypedArray()

        @Child
        var bodyNode: ValueNode = emitValueExpr(expr.expr)

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

    inner class FnBodyNode(expr: FnExpr) : ValueNode() {
        @Children
        val readArgNodes = expr.params
            .mapIndexed { idx, it -> WriteLocalVarNodeGen.create(ReadArgNode(idx), frameDescriptor.findOrAddFrameSlot(it)) }
            .toTypedArray()

        @Child
        var bodyNode: ValueNode = emitValueExpr(expr.expr)

        override fun execute(frame: VirtualFrame): Any {
            for (node in readArgNodes) {
                node.execute(frame)
            }

            return bodyNode.execute(frame)
        }
    }

    inner class CallNode(expr: CallExpr) : ValueNode() {
        @Child
        var callNode = Truffle.getRuntime().createIndirectCallNode()!!

        @Child
        var fnNode = emitValueExpr(expr.f)

        @Children
        val argNodes =
            (listOfNotNull(expr.effectArg) + expr.args).map(::emitValueExpr).toTypedArray()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val fn = expectBridjeFunction(fnNode.execute(frame))

            val argValues = arrayOfNulls<Any>(argNodes.size)

            for (i in argNodes.indices) {
                argValues[i] = argNodes[i].execute(frame)
            }

            return callNode.call(fn.callTarget, *argValues)
        }
    }

    inner class CaseMatched(val res: Any) : ControlFlowException()

    inner class CaseClauseNode(dataSlot: FrameSlot, clause: CaseClause) : Node() {

        @Child
        var readSlot = ReadLocalVarNodeGen.create(dataSlot)!!

        @Children
        val writeBindingNodes =
            clause.bindings.mapIndexed { idx, lv ->
                WriteLocalVarNodeGen.create(
                    ReadVariantParamNode(ReadLocalVarNodeGen.create(dataSlot), idx),
                    frameDescriptor.findOrAddFrameSlot(lv))
            }.toTypedArray()

        @Child
        var exprNode = emitValueExpr(clause.bodyExpr)

        private val conditionProfile = ConditionProfile.createBinaryProfile()!!
        private val variantSym = clause.variantKey.sym

        @ExplodeLoop
        fun execute(frame: VirtualFrame) {
            val value = expectVariantObject(readSlot.execute(frame))

            if (conditionProfile.profile(value.variantKey.sym == variantSym)) {
                for (node in writeBindingNodes) {
                    node.execute(frame)
                }

                throw CaseMatched(exprNode.execute(frame))
            }
        }
    }

    inner class CaseExprNode(expr: CaseExpr) : ValueNode() {
        private val dataSlot: FrameSlot = frameDescriptor.findOrAddFrameSlot(this)

        @Child
        var exprNode = WriteLocalVarNodeGen.create(emitValueExpr(expr.expr), dataSlot)!!

        @Children
        val clauseNodes = expr.clauses.map { CaseClauseNode(dataSlot, it) }.toTypedArray()

        @Child
        var defaultNode = expr.defaultExpr?.let(::emitValueExpr)

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

    internal class LoopReturnException(val res: Any) : ControlFlowException()
    internal object RecurException : ControlFlowException()

    inner class LoopNode(expr: LoopExpr) : ValueNode() {
        @Children
        val bindingNodes = expr.bindings
            .map {
                WriteLocalVarNodeGen.create(
                    emitValueExpr(it.expr),
                    frameDescriptor.findOrAddFrameSlot(it.localVar))
            }
            .toTypedArray()

        @Child
        var bodyNode = emitValueExpr(expr.expr)


        @Child
        var loopBodyNode = Truffle.getRuntime().createLoopNode(object : Node(), RepeatingNode {
            override fun executeRepeating(frame: VirtualFrame): Boolean {
                try {
                    throw LoopReturnException(bodyNode.execute(frame))
                } catch (e: RecurException) {
                    return true
                }
            }

        })

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            for (node in bindingNodes) {
                node.execute(frame)
            }

            try {
                loopBodyNode.executeLoop(frame)
                throw RuntimeException("Loop didn't exit properly")
            } catch (e: LoopReturnException) {
                return e.res
            }
        }
    }

    inner class RecurNode(expr: RecurExpr) : ValueNode() {
        @Children
        val recurNodes = expr.exprs.map {
            WriteLocalVarNodeGen.create(
                emitValueExpr(it.second),
                frameDescriptor.findOrAddFrameSlot(it.first))
        }.toTypedArray()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            for (node in recurNodes) {
                node.execute(frame)
            }

            throw RecurException
        }
    }

    internal class ReadFxNode(val slot: FrameSlot) : Node() {
        @Suppress("UNCHECKED_CAST")
        fun execute(frame: VirtualFrame) = (frame.getObject(slot) as? List<FxMap>)
    }

    inner class WithFxNode(expr: WithFxExpr) : ValueNode() {
        inner class UpdateFxNode(expr: WithFxExpr) : ValueNode() {
            @Child
            var readFxNode = ReadFxNode(frameDescriptor.findOrAddFrameSlot(expr.oldFxLocal))

            inner class UpdateEffectNode(val sym: QSymbol, expr: WithFxExpr, fnExpr: FnExpr) : Node() {
                @Child
                var bodyNode = emitValueExpr(fnExpr.copy(params = listOf(expr.oldFxLocal) + fnExpr.params))

                fun execute(frame: VirtualFrame) =
                    Pair(sym, expectBridjeFunction(bodyNode.execute(frame)))
            }

            @Children
            val effectNodes = expr.fx.map { UpdateEffectNode(it.effectVar.sym, expr, it.fnExpr) }.toTypedArray()

            @TruffleBoundary
            private fun updateFxMap(fx: List<FxMap>?, newFx: Array<Pair<QSymbol, BridjeFunction>?>): List<FxMap> {
                return listOf((fx?.first() ?: emptyMap()) + newFx.filterNotNull()) + fx.orEmpty()
            }

            @ExplodeLoop
            override fun execute(frame: VirtualFrame): List<FxMap> {
                val newFx = arrayOfNulls<Pair<QSymbol, BridjeFunction>>(effectNodes.size)

                for (i in 0 until effectNodes.size) {
                    newFx[i] = effectNodes[i].execute(frame)
                }

                return updateFxMap(readFxNode.execute(frame), newFx)
            }
        }

        @Child
        var writeFxNode = WriteLocalVarNodeGen.create(UpdateFxNode(expr), frameDescriptor.findOrAddFrameSlot(expr.newFxLocal))

        @Child
        var bodyNode = emitValueExpr(expr.bodyExpr)

        override fun execute(frame: VirtualFrame): Any {
            writeFxNode.execute(frame)

            return bodyNode.execute(frame)
        }
    }

    fun makeRootNode(node: ValueNode): RootNode = makeRootNode(node, frameDescriptor)

    fun emitValueExpr(expr: ValueExpr): ValueNode =
        when (expr) {
            is BooleanExpr -> BoolNode(expr.boolean)
            is StringExpr -> ObjectNode(expr.string)
            is IntExpr -> IntNode(expr.int)
            is BigIntExpr -> ObjectNode(expr.bigInt)
            is FloatExpr -> FloatNode(expr.float)
            is BigFloatExpr -> ObjectNode(expr.bigFloat)

            is QuotedSymbolExpr -> ObjectNode(expr.sym)
            is QuotedQSymbolExpr -> ObjectNode(expr.sym)

            is VectorExpr -> VectorNode(expr)
            is SetExpr -> SetNode(expr)

            is RecordExpr -> RecordNode(expr)

            is FnExpr -> {
                val emitter = ValueExprEmitter()
                ObjectNode(BridjeFunction(emitter.makeRootNode(emitter.FnBodyNode(expr))))
            }

            is CallExpr -> CallNode(expr)

            is IfExpr -> IfNode(expr)
            is DoExpr -> DoNode(expr)
            is LetExpr -> LetNode(expr)

            is LoopExpr -> LoopNode(expr)
            is RecurExpr -> RecurNode(expr)

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.localVar))
            is GlobalVarExpr -> ObjectNode(expr.globalVar.value!!)

            is WithFxExpr -> WithFxNode(expr)

            is CaseExpr -> CaseExprNode(expr)
        }

    inner class WrapHostObjectNode(@Child var node: ValueNode) : ValueNode() {
        override fun execute(frame: VirtualFrame): Any = getCtx().truffleEnv.asGuestValue(node.execute(frame))
    }

    inner class WrapFxNode(@Child var node: ValueNode) : ValueNode() {
        @Child
        var writeFxVarNode = WriteLocalVarNodeGen.create(
            ObjectNode(listOf(emptyMap<QSymbol, BridjeFunction>())),
            frameDescriptor.findOrAddFrameSlot(DEFAULT_EFFECT_LOCAL))

        override fun execute(frame: VirtualFrame): Any {
            writeFxVarNode.execute(frame)

            return node.execute(frame)
        }
    }

    companion object {
        internal fun emitValueExpr(expr: ValueExpr): CallTarget {
            val emitter = ValueExprEmitter()
            return createCallTarget(emitter.makeRootNode(
                emitter.WrapHostObjectNode(
                    emitter.WrapFxNode(
                        emitter.emitValueExpr(expr)))))
        }

        internal fun evalValueExpr(expr: ValueExpr) = emitValueExpr(expr).call()!!
    }
}

