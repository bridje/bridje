package brj

import brj.BridjeTypesGen.*
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

internal class ValueExprEmitter(val ctx: BridjeContext) {

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

    class ConstantNode(val obj: Any) : ValueNode() {
        override fun execute(frame: VirtualFrame) = obj
    }

    inner class HostObjectNode(obj: Any) : ValueNode() {
        val interopObj = ctx.truffleEnv.asGuestValue(obj)

        override fun execute(frame: VirtualFrame) = interopObj
    }


    inner class CollNode(exprs: List<ValueExpr>) : ValueNode() {
        @Children
        val elNodes = exprs.map(this@ValueExprEmitter::emitValueNode).toTypedArray()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Array<*> {
            val els = arrayOfNulls<Any>(elNodes.size)

            for (i in 0 until elNodes.size) {
                els[i] = elNodes[i].execute(frame)
            }

            return els
        }
    }

    inner class VectorNode(expr: VectorExpr) : ValueNode() {
        @Child
        var collNode = CollNode(expr.exprs)

        @TruffleBoundary
        private fun makeVector(els: Array<*>) = els.toList()

        override fun execute(frame: VirtualFrame) = ctx.truffleEnv.asGuestValue(makeVector(collNode.execute(frame)))
    }

    inner class SetNode(expr: SetExpr) : ValueNode() {
        @Child
        var collNode = CollNode(expr.exprs)

        @TruffleBoundary
        private fun makeSet(els: Array<*>) = els.toSet()

        override fun execute(frame: VirtualFrame) = ctx.truffleEnv.asGuestValue(makeSet(collNode.execute(frame)))
    }

    inner class RecordNode(expr: RecordExpr) : ValueNode() {
        val factory = RecordEmitter(ctx).recordObjectFactory(expr.entries.map(RecordEntry::recordKey))

        @Children
        val valNodes = expr.entries.map { emitValueNode(it.expr) }.toTypedArray()

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
        val exprNodes = expr.exprs.map(::emitValueNode).toTypedArray()
        @Child
        var exprNode = emitValueNode(expr.expr)

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
        var predNode = emitValueNode(expr.predExpr)
        @Child
        var thenNode = emitValueNode(expr.thenExpr)
        @Child
        var elseNode = emitValueNode(expr.elseExpr)

        private val conditionProfile = ConditionProfile.createBinaryProfile()

        override fun execute(frame: VirtualFrame): Any =
            (if (conditionProfile.profile(expectBoolean(predNode.execute(frame)))) thenNode else elseNode).execute(frame)
    }

    inner class LetNode(expr: LetExpr) : ValueNode() {
        @Children
        val bindingNodes = expr.bindings
            .map { WriteLocalVarNodeGen.create(emitValueNode(it.expr), frameDescriptor.findOrAddFrameSlot(it.localVar)) }
            .toTypedArray()

        @Child
        var bodyNode: ValueNode = emitValueNode(expr.expr)

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

    class GlobalVarNode(val globalVar: GlobalVar) : ValueNode() {
        // TODO specialise/cache
        override fun execute(frame: VirtualFrame) = globalVar.value!!
    }

    inner class FnBodyNode(expr: FnExpr) : ValueNode() {
        @Children
        val readArgNodes = expr.params
            .mapIndexed { idx, it -> WriteLocalVarNodeGen.create(ReadArgNode(idx), frameDescriptor.findOrAddFrameSlot(it)) }
            .toTypedArray()

        @Child
        var bodyNode: ValueNode = emitValueNode(expr.expr)

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
        var fnNode = emitValueNode(expr.f)

        @Children
        val argNodes =
            (listOfNotNull(expr.effectArg) + expr.args).map(::emitValueNode).toTypedArray()

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
        var exprNode = emitValueNode(clause.bodyExpr)

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
        var exprNode = WriteLocalVarNodeGen.create(emitValueNode(expr.expr), dataSlot)!!

        @Children
        val clauseNodes = expr.clauses.map { CaseClauseNode(dataSlot, it) }.toTypedArray()

        @Child
        var defaultNode = expr.defaultExpr?.let(::emitValueNode)

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
                    emitValueNode(it.expr),
                    frameDescriptor.findOrAddFrameSlot(it.localVar))
            }
            .toTypedArray()

        @Child
        var bodyNode = emitValueNode(expr.expr)


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
                emitValueNode(it.second),
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
                var bodyNode = emitValueNode(fnExpr.copy(params = listOf(expr.oldFxLocal) + fnExpr.params))

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
        var bodyNode = emitValueNode(expr.bodyExpr)

        override fun execute(frame: VirtualFrame): Any {
            writeFxNode.execute(frame)

            return bodyNode.execute(frame)
        }
    }

    private fun makeRootNode(node: ValueNode): RootNode = ctx.makeRootNode(node, frameDescriptor)

    private fun emitValueNode(expr: ValueExpr): ValueNode =
        when (expr) {
            is BooleanExpr -> BoolNode(expr.boolean)
            is StringExpr -> ConstantNode(expr.string)
            is IntExpr -> IntNode(expr.int)
            is BigIntExpr -> HostObjectNode(expr.bigInt)
            is FloatExpr -> FloatNode(expr.float)
            is BigFloatExpr -> HostObjectNode(expr.bigFloat)

            is QuotedSymbolExpr -> HostObjectNode(expr.sym)
            is QuotedQSymbolExpr -> HostObjectNode(expr.sym)

            is VectorExpr -> VectorNode(expr)
            is SetExpr -> SetNode(expr)

            is RecordExpr -> RecordNode(expr)

            is FnExpr -> {
                val emitter = ValueExprEmitter(ctx)
                ConstantNode(BridjeFunction(emitter.makeRootNode(emitter.FnBodyNode(expr))))
            }

            is CallExpr -> CallNode(expr)

            is IfExpr -> IfNode(expr)
            is DoExpr -> DoNode(expr)
            is LetExpr -> LetNode(expr)

            is LoopExpr -> LoopNode(expr)
            is RecurExpr -> RecurNode(expr)

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.localVar))
            is GlobalVarExpr -> GlobalVarNode(expr.globalVar)

            is WithFxExpr -> WithFxNode(expr)

            is CaseExpr -> CaseExprNode(expr)
        }

    inner class WrapFxNode(@Child var node: ValueNode) : ValueNode() {
        @Child
        var writeFxVarNode = WriteLocalVarNodeGen.create(
            HostObjectNode(listOf(emptyMap<QSymbol, BridjeFunction>())),
            frameDescriptor.findOrAddFrameSlot(DEFAULT_EFFECT_LOCAL))

        override fun execute(frame: VirtualFrame): Any {
            writeFxVarNode.execute(frame)

            return node.execute(frame)
        }
    }

    internal fun emitValueExpr(expr: ValueExpr): CallTarget {
        return Truffle.getRuntime().createCallTarget(makeRootNode(
            WrapFxNode(
                emitValueNode(expr))))
    }

    internal fun evalValueExpr(expr: ValueExpr) = emitValueExpr(expr).call()!!
}
