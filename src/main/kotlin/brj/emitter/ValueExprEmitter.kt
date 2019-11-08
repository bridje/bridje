package brj.emitter

import brj.BridjeContext
import brj.BridjeLanguage
import brj.Loc
import brj.analyser.*
import brj.emitter.BridjeTypesGen.*
import brj.runtime.GlobalVar
import brj.runtime.QSymbol
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.ConditionProfile

// HACK
internal typealias FxMap = Map<QSymbol, BridjeFunction>

internal class ValueExprEmitter(val ctx: BridjeContext) {

    val frameDescriptor = FrameDescriptor()

    class BoolNode(val boolean: Boolean, override val loc: Loc?) : ValueNode() {
        override fun execute(frame: VirtualFrame): Boolean = boolean
    }

    class IntNode(val int: Long, override val loc: Loc?) : ValueNode() {
        override fun execute(frame: VirtualFrame): Long = int
    }

    class FloatNode(val float: Double, override val loc: Loc?) : ValueNode() {
        override fun execute(frame: VirtualFrame): Double = float
    }

    data class ConstantNode(val obj: Any, override val loc: Loc?) : ValueNode() {
        override fun execute(frame: VirtualFrame) = obj
    }

    class UnwrapNode(@Child var node: ValueNode, val truffleEnv: TruffleLanguage.Env) : ValueNode() {
        private val profile = ConditionProfile.createBinaryProfile()

        override fun execute(frame: VirtualFrame): Any {
            val res = node.execute(frame)
            return if (profile.profile(truffleEnv.isHostObject(res))) truffleEnv.asHostObject(res) else res
        }
    }

    class VectorNode(@Child var elsNode: ArrayNode, val truffleEnv: TruffleLanguage.Env, override val loc: Loc?) : ValueNode() {
        @TruffleBoundary(allowInlining = true)
        private fun makeVector(els: Array<*>) = els.toList()

        override fun execute(frame: VirtualFrame) = truffleEnv.asGuestValue(makeVector(elsNode.execute(frame)))
    }

    private fun emitVector(expr: VectorExpr) =
        VectorNode(ArrayNode(expr.exprs.map(this::emitValueNode).map { UnwrapNode(it, ctx.truffleEnv) }.toTypedArray()), ctx.truffleEnv, expr.loc)

    class SetNode(@Child var elsNode: ArrayNode, val truffleEnv: TruffleLanguage.Env, override val loc: Loc?) : ValueNode() {
        @TruffleBoundary(allowInlining = true)
        private fun makeSet(els: Array<*>) = els.toSet()

        override fun execute(frame: VirtualFrame) = truffleEnv.asGuestValue(makeSet(elsNode.execute(frame)))
    }

    private fun emitSet(expr: SetExpr) =
        SetNode(ArrayNode(expr.exprs.map(this::emitValueNode).map { UnwrapNode(it, ctx.truffleEnv) }.toTypedArray()), ctx.truffleEnv, expr.loc)

    inner class RecordNode(expr: RecordExpr) : ValueNode() {
        override val loc = expr.loc
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

    class DoNode(@Children val exprNodes: Array<ValueNode>, @Child var exprNode: ValueNode, override val loc: Loc?) : ValueNode() {
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

    private fun emitDo(expr: DoExpr) =
        DoNode(expr.exprs.map(::emitValueNode).toTypedArray(), emitValueNode(expr.expr), expr.loc)

    inner class IfNode(expr: IfExpr) : ValueNode() {
        override val loc = expr.loc

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

    class LetNode(@Children val bindingNodes: Array<WriteLocalVarNode>, @Child var bodyNode: ValueNode, override val loc: Loc? = null) : ValueNode() {
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

    private fun emitLet(expr: LetExpr) =
        LetNode(
            expr.bindings
                .map { WriteLocalVarNodeGen.create(emitValueNode(it.expr), frameDescriptor.findOrAddFrameSlot(it.localVar)) }
                .toTypedArray(),
            emitValueNode(expr.expr),
            expr.loc)

    internal class WriteLocalsNode(@Children val writeParamLocalNodes: Array<WriteLocalVarNode>,
                                   @Child var bodyNode: ValueNode,
                                   override val loc: Loc? = null) : ValueNode() {

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            for (i in writeParamLocalNodes.indices) {
                writeParamLocalNodes[i].execute(frame)
            }

            return bodyNode.execute(frame)
        }
    }

    class FnRootNode(lang: BridjeLanguage, frameDescriptor: FrameDescriptor,
                     @Child var bodyNode: ValueNode) : RootNode(lang, frameDescriptor) {

        override fun execute(frame: VirtualFrame): Any {
            return bodyNode.execute(frame)
        }
    }

    class WriteLexLocalNode(): ValueNode() {
        override fun execute(frame: VirtualFrame): Any {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    class FnNode(@Children val writeLexLocalNodes: Array<WriteLexLocalNode>,
                 val fnRootNode: RootNode,
                 val lexFrameDescriptor: FrameDescriptor,
                 override val loc: Loc?) : ValueNode() {
        val emptyArgs = emptyArray<Any>()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val lexFrame = Truffle.getRuntime().createMaterializedFrame(emptyArgs, lexFrameDescriptor)

            for (i in writeLexLocalNodes.indices) {
                writeLexLocalNodes[i].execute(lexFrame)
            }

            return BridjeFunction(fnRootNode, lexFrame)
        }
    }

    class ReadLexLocalVarNode(@Child var readLocalNode: ReadLocalVarNode) : ValueNode() {
        override fun execute(frame: VirtualFrame) = readLocalNode.execute(expectVirtualFrame(frame.arguments[0]))
    }

    fun emitFnExpr(fnExpr: FnExpr): ValueNode {
        val innerEmitter = ValueExprEmitter(ctx)
        val innerFrameDescriptor = innerEmitter.frameDescriptor

        val argNodes = fnExpr.params.mapIndexed { idx, lv ->
            WriteLocalVarNodeGen.create(ReadArgNode(idx + 1), innerFrameDescriptor.findOrAddFrameSlot(lv))
        }

        val lexFrameDescriptor = FrameDescriptor()

        val readLexLocalNodes = fnExpr.closedOverLocals.map { lv ->
            WriteLocalVarNodeGen.create(
                ReadLexLocalVarNode(ReadLocalVarNodeGen.create(lexFrameDescriptor.findOrAddFrameSlot(lv))),
                innerFrameDescriptor.findOrAddFrameSlot(lv))
        }

        val writeLexLocalNodes: List<WriteLexLocalNode> = fnExpr.closedOverLocals.map { TODO() }

        val fnBodyNode = WriteLocalsNode(
            (argNodes + readLexLocalNodes).toTypedArray(),
            innerEmitter.emitValueNode(fnExpr.expr),
            fnExpr.expr.loc)

        return FnNode(writeLexLocalNodes.toTypedArray(), FnRootNode(ctx.language, innerFrameDescriptor, fnBodyNode), lexFrameDescriptor, fnExpr.loc)
    }

    class GlobalVarNode(val globalVar: GlobalVar, override val loc: Loc?) : ValueNode() {
        @CompilationFinal
        var value: Any? = null

        override fun execute(frame: VirtualFrame): Any {
            if (value == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate()
                value = globalVar.value!!

                if (globalVar.type.effects.isNotEmpty()) TODO()
            }

            return value!!
        }
    }

    private fun emitGlobalVar(expr: GlobalVarExpr): ValueNode = GlobalVarNode(expr.globalVar, expr.loc)

    class CallNode(@Child var fnNode: ValueNode, @Children val argNodes: Array<ValueNode>, override val loc: Loc? = null) : ValueNode() {
        @Child
        var callNode: IndirectCallNode = Truffle.getRuntime().createIndirectCallNode()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val fn = expectBridjeFunction(fnNode.execute(frame))

            val argValues = arrayOfNulls<Any>(argNodes.size + 1)

            argValues[0] = fn.lexFrame
            for (i in argNodes.indices) {
                argValues[i + 1] = argNodes[i].execute(frame)
            }

            return callNode.call(fn.callTarget, *argValues)
        }
    }

    // TODO optimise for case of globalvarexpr with closure
    private fun emitCallNode(expr: CallExpr): ValueNode =
        CallNode(emitValueNode(expr.f), expr.args.map(::emitValueNode).toTypedArray(), expr.loc)

    class CaseMatched(val res: Any) : ControlFlowException()

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
        override val loc = expr.loc

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
        override val loc = expr.loc

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
        })!!

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            for (node in bindingNodes) {
                node.execute(frame)
            }

            try {
                loopBodyNode.executeLoop(frame)
                throw IllegalStateException("Loop didn't exit properly")
            } catch (e: LoopReturnException) {
                return e.res
            }
        }
    }

    inner class RecurNode(expr: RecurExpr) : ValueNode() {
        override val loc = expr.loc

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

    internal abstract class FxMapNode : Node() {
        abstract fun execute(frame: VirtualFrame): FxMap
    }

    internal class ReadFxMapNode(val slot: FrameSlot) : FxMapNode() {
        @Suppress("UNCHECKED_CAST")
        override fun execute(frame: VirtualFrame) = (frame.getObject(slot) as FxMap)
    }

    class UpdateEffectNode(val sym: QSymbol, @Child var bodyNode: ValueNode) : Node() {
        fun execute(frame: VirtualFrame) = Pair(sym, expectBridjeFunction(bodyNode.execute(frame)))
    }

    class UpdateFxNode(@Child var readFxMapNode: FxMapNode,
                       @Children val effectNodes: Array<UpdateEffectNode>,
                       override val loc: Loc? = null) : ValueNode() {

        @TruffleBoundary
        private fun updateFxMap(fxMap: FxMap, newFx: Array<Pair<QSymbol, BridjeFunction>?>) = fxMap + newFx.filterNotNull()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): FxMap {
            val newFx = arrayOfNulls<Pair<QSymbol, BridjeFunction>>(effectNodes.size)

            for (i in effectNodes.indices) {
                newFx[i] = effectNodes[i].execute(frame)
            }

            return updateFxMap(readFxMapNode.execute(frame), newFx)
        }
    }

    private fun emitWithFx(expr: WithFxExpr): ValueNode {
        val readFxNode = ReadFxMapNode(frameDescriptor.findOrAddFrameSlot(expr.oldFxLocal))
        val effectNodes = expr.fx.map {
            UpdateEffectNode(it.effectVar.sym, emitFnExpr(it.fnExpr))
        }.toTypedArray()

        return LetNode(
            arrayOf(WriteLocalVarNodeGen.create(UpdateFxNode(readFxNode, effectNodes, expr.loc), frameDescriptor.findOrAddFrameSlot(expr.newFxLocal))),
            emitValueNode(expr.bodyExpr),
            expr.loc)
    }

    private fun makeRootNode(node: ValueNode): RootNode = ctx.makeRootNode(node, frameDescriptor)

    private fun emitValueNode(expr: ValueExpr): ValueNode =
        when (expr) {
            is BooleanExpr -> BoolNode(expr.boolean, expr.loc)
            is StringExpr -> ConstantNode(expr.string, expr.loc)
            is IntExpr -> IntNode(expr.int, expr.loc)
            is BigIntExpr -> ConstantNode(ctx.truffleEnv.asGuestValue(expr.bigInt), expr.loc)
            is FloatExpr -> FloatNode(expr.float, expr.loc)
            is BigFloatExpr -> ConstantNode(ctx.truffleEnv.asGuestValue(expr.bigFloat), expr.loc)

            is QuotedSymbolExpr -> ConstantNode(expr.sym, expr.loc)
            is QuotedQSymbolExpr -> ConstantNode(expr.sym, expr.loc)

            is VectorExpr -> emitVector(expr)
            is SetExpr -> emitSet(expr)

            is RecordExpr -> RecordNode(expr)

            is FnExpr -> emitFnExpr(expr)

            is CallExpr -> emitCallNode(expr)

            is IfExpr -> IfNode(expr)
            is DoExpr -> emitDo(expr)
            is LetExpr -> emitLet(expr)

            is LoopExpr -> LoopNode(expr)
            is RecurExpr -> RecurNode(expr)

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.localVar))
            is GlobalVarExpr -> emitGlobalVar(expr)

            is WithFxExpr -> emitWithFx(expr)

            is CaseExpr -> CaseExprNode(expr)
        }

    inner class WrapFxNode(@Child var node: ValueNode) : ValueNode() {
        @Child
        var writeFxVarNode = WriteLocalVarNodeGen.create(
            ConstantNode(emptyMap<QSymbol, BridjeFunction>(), null),
            frameDescriptor.findOrAddFrameSlot(DEFAULT_EFFECT_LOCAL))!!

        override fun execute(frame: VirtualFrame): Any {
            writeFxVarNode.execute(frame)

            return node.execute(frame)
        }
    }

    internal fun evalValueExpr(expr: ValueExpr) =
        Truffle.getRuntime().createCallTarget(makeRootNode(
            WrapFxNode(
                emitValueNode(expr)))).call()!!
}
