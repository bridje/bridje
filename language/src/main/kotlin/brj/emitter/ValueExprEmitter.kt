package brj.emitter

import brj.BridjeContext
import brj.Loc
import brj.analyser.*
import brj.emitter.BridjeTypesGen.*
import brj.runtime.BridjeFunction
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

// TODO this probably wants to be a DynamicObject
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

    data class ConstantNode(val obj: Any?, override val loc: Loc?) : ValueNode() {
        override fun execute(frame: VirtualFrame) = obj
    }

    class UnwrapNode(@Child var node: ValueNode, val truffleEnv: TruffleLanguage.Env) : ValueNode() {
        private val profile = ConditionProfile.createBinaryProfile()

        override fun execute(frame: VirtualFrame): Any? {
            val res = node.execute(frame)
            return if (profile.profile(truffleEnv.isHostObject(res))) truffleEnv.asHostObject(res) else res
        }
    }

    class VectorNode(@Child var elsNode: ArrayNode<*>, val truffleEnv: TruffleLanguage.Env, override val loc: Loc?) : ValueNode() {
        @TruffleBoundary(allowInlining = true)
        private fun makeVector(els: Array<*>) = els.toList()

        override fun execute(frame: VirtualFrame): Any = truffleEnv.asGuestValue(makeVector(elsNode.execute(frame)))
    }

    private fun emitVector(expr: VectorExpr) =
        VectorNode(ArrayNode(expr.exprs.map(this::emitValueNode).map { UnwrapNode(it, ctx.truffleEnv) }.toTypedArray()), ctx.truffleEnv, expr.loc)

    class SetNode(@Child var elsNode: ArrayNode<*>, val truffleEnv: TruffleLanguage.Env, override val loc: Loc?) : ValueNode() {
        @TruffleBoundary(allowInlining = true)
        private fun makeSet(els: Array<*>) = els.toSet()

        override fun execute(frame: VirtualFrame) = truffleEnv.asGuestValue(makeSet(elsNode.execute(frame)))
    }

    private fun emitSet(expr: SetExpr) =
        SetNode(ArrayNode(expr.exprs.map(this::emitValueNode).map { UnwrapNode(it, ctx.truffleEnv) }.toTypedArray()), ctx.truffleEnv, expr.loc)

    class RecordNode(private val factory: RecordObjectFactory, @Child private var valArrayNode: ArrayNode<*>, override val loc: Loc?) : ValueNode() {

        @TruffleBoundary
        private fun buildRecord(vals: Array<Any?>) = factory(vals)

        @ExplodeLoop
        override fun execute(frame: VirtualFrame) = buildRecord(valArrayNode.execute(frame))
    }

    private fun emitRecord(expr: RecordExpr): ValueNode =
        RecordNode(
            RecordEmitter(ctx).recordObjectFactory(expr.entries.map(RecordEntry::recordKey)),
            ArrayNode(expr.entries.map { emitValueNode(it.expr) }.toTypedArray()),
            expr.loc)

    class DoNode(@Children val exprNodes: Array<ValueNode>, @Child var exprNode: ValueNode, override val loc: Loc?) : ValueNode() {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any? {
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

    class IfNode(@Child var predNode: ValueNode, @Child var thenNode: ValueNode, @Child var elseNode: ValueNode, override val loc: Loc?) : ValueNode() {
        private val conditionProfile = ConditionProfile.createBinaryProfile()

        override fun execute(frame: VirtualFrame) =
            (if (conditionProfile.profile(expectBoolean(predNode.execute(frame)))) thenNode else elseNode).execute(frame)
    }

    fun emitIf(expr: IfExpr) = IfNode(emitValueNode(expr.predExpr), emitValueNode(expr.thenExpr), emitValueNode(expr.elseExpr), expr.loc)

    class LetNode(@Children val bindingNodes: Array<WriteLocalVarNode>, @Child var bodyNode: ValueNode, override val loc: Loc? = null) : ValueNode() {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any? {
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
        override fun execute(frame: VirtualFrame): Any? {
            for (i in writeParamLocalNodes.indices) {
                writeParamLocalNodes[i].execute(frame)
            }

            return bodyNode.execute(frame)
        }
    }

    class LexFrameNode(@Children val writeLexLocalNodes: Array<WriteLocalVarNode>, val lexFrameDescriptor: FrameDescriptor) : ValueNode() {
        private val emptyArgs = emptyArray<Any>()

        override fun execute(frame: VirtualFrame): Any? {
            val lexFrame = Truffle.getRuntime().createMaterializedFrame(emptyArgs, lexFrameDescriptor)

            for (i in writeLexLocalNodes.indices) {
                writeLexLocalNodes[i].execute(lexFrame)
            }

            return lexFrame
        }
    }

    class FnNode(@Child var lexObjNode: ValueNode, val fnRootNode: RootNode, override val loc: Loc?) : ValueNode() {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): BridjeFunction = BridjeFunction(fnRootNode, lexObjNode.execute(frame))
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

        val (lexObjNode, readLexLocalNodes) = when (fnExpr.closedOverLocals.size) {
            0 -> Pair(ConstantNode(null, null), emptyList<WriteLocalVarNode>())

            1 -> {
                val localVar = fnExpr.closedOverLocals.first()

                Pair(
                    when (val lv = if (localVar == DEFAULT_EFFECT_LOCAL) fnExpr.effectLocal else localVar) {
                        null -> ConstantNode(emptyMap<QSymbol, BridjeFunction>(), null)

                        else -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(lv))
                    },

                    listOf(WriteLocalVarNodeGen.create(ReadArgNode(0), innerFrameDescriptor.findOrAddFrameSlot(localVar))))
            }

            else -> {
                val lexFrameDescriptor = FrameDescriptor()

                val writeLexLocalNodes = fnExpr.closedOverLocals
                    .mapNotNull { if (it == DEFAULT_EFFECT_LOCAL) fnExpr.effectLocal else it }
                    .map {
                        WriteLocalVarNodeGen.create(
                            ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(it)),
                            lexFrameDescriptor.findOrAddFrameSlot(it))
                    }

                val readLexLocalNodes = fnExpr.closedOverLocals.map { lv ->
                    WriteLocalVarNodeGen.create(
                        ReadLexLocalVarNode(ReadLocalVarNodeGen.create(lexFrameDescriptor.findOrAddFrameSlot(lv))),
                        innerFrameDescriptor.findOrAddFrameSlot(lv))
                }

                Pair(LexFrameNode(writeLexLocalNodes.toTypedArray(), lexFrameDescriptor), readLexLocalNodes)
            }
        }


        val fnBodyNode = WriteLocalsNode(
            (argNodes + readLexLocalNodes).toTypedArray(),
            innerEmitter.emitValueNode(fnExpr.expr),
            fnExpr.expr.loc)

        return FnNode(lexObjNode, ctx.makeRootNode(fnBodyNode, innerFrameDescriptor), fnExpr.loc)
    }

    class GlobalVarNode(val globalVar: GlobalVar, override val loc: Loc?) : ValueNode() {
        @CompilationFinal
        var value: Any? = null

        override fun execute(frame: VirtualFrame): Any {
            if (value == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate()
                value = globalVar.value!!
            }

            return value!!
        }
    }

    class EffectfulGlobalVarNode(@Child var globalVarNode: GlobalVarNode, @Child var readEffectsNode: ValueNode) : ValueNode() {
        override fun execute(frame: VirtualFrame) =
            BridjeFunction(expectBridjeFunction(globalVarNode.execute(frame)).rootNode, readEffectsNode.execute(frame))
    }

    private fun emitGlobalVar(expr: GlobalVarExpr): ValueNode {
        val globalVarNode = GlobalVarNode(expr.globalVar, expr.loc)

        return if (expr.globalVar.type.effects.isNotEmpty())
            EffectfulGlobalVarNode(globalVarNode, ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.effectLocal)))
        else globalVarNode
    }

    class CallNode(@Child var fnNode: ValueNode, @Children val argNodes: Array<ValueNode>, override val loc: Loc? = null) : ValueNode() {
        @Child
        var callNode: IndirectCallNode = Truffle.getRuntime().createIndirectCallNode()

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            val fn = expectBridjeFunction(fnNode.execute(frame))

            val argValues = arrayOfNulls<Any>(argNodes.size + 1)

            argValues[0] = fn.lexObj
            for (i in argNodes.indices) {
                argValues[i + 1] = argNodes[i].execute(frame)
            }

            return callNode.call(fn.callTarget, *argValues)
        }
    }

    // TODO optimise for case of globalvarexpr with closure
    private fun emitCallNode(expr: CallExpr): ValueNode =
        CallNode(emitValueNode(expr.f), expr.args.map(::emitValueNode).toTypedArray(), expr.loc)

    class CaseMatched(val res: Any?) : ControlFlowException()

    class CaseClauseNode(@Child private var caseValueNode: ReadLocalVarNode,
                         @Children private val writeBindingNodes: Array<WriteLocalVarNode>,
                         @Child private var exprNode: ValueNode,
                         private val variantSym: QSymbol) : Node() {

        private val conditionProfile = ConditionProfile.createBinaryProfile()!!

        @ExplodeLoop
        fun execute(frame: VirtualFrame) {
            val value = expectVariantObject(caseValueNode.execute(frame))

            if (conditionProfile.profile(value.variantKey.sym == variantSym)) {
                for (node in writeBindingNodes) {
                    node.execute(frame)
                }

                throw CaseMatched(exprNode.execute(frame))
            }
        }
    }

    private fun emitCaseClauseNode(dataSlot: FrameSlot, clause: CaseClause): CaseClauseNode =
        CaseClauseNode(ReadLocalVarNodeGen.create(dataSlot),
            clause.bindings.mapIndexed { idx, lv ->
                WriteLocalVarNodeGen.create(
                    ReadVariantParamNode(ReadLocalVarNodeGen.create(dataSlot), idx),
                    frameDescriptor.findOrAddFrameSlot(lv))
            }.toTypedArray(),
            emitValueNode(clause.bodyExpr),
            clause.variantKey.sym)

    class CaseExprNode(@Child private var exprNode: WriteLocalVarNode,
                       @Children private val clauseNodes: Array<CaseClauseNode>,
                       @Child var defaultNode: ValueNode?,
                       override var loc: Loc?) : ValueNode() {

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any? {
            exprNode.execute(frame)

            try {
                for (node in clauseNodes) {
                    node.execute(frame)
                }
            } catch (e: CaseMatched) {
                return e.res
            }

            return defaultNode!!.execute(frame)
        }
    }

    private fun emitCase(expr: CaseExpr): ValueNode {
        val dataSlot: FrameSlot = frameDescriptor.findOrAddFrameSlot(this)

        return CaseExprNode(
            WriteLocalVarNodeGen.create(emitValueNode(expr.expr), dataSlot)!!,
            expr.clauses.map { emitCaseClauseNode(dataSlot, it) }.toTypedArray(),
            expr.defaultExpr?.let(::emitValueNode),
            expr.loc)
    }

    internal class LoopReturnException(val res: Any?) : ControlFlowException()
    internal object RecurException : ControlFlowException()

    class LoopNode(@Children val bindingNodes: Array<WriteLocalVarNode>, @Child var bodyNode: ValueNode, override val loc: Loc?) : ValueNode() {
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
        override fun execute(frame: VirtualFrame): Any? {
            for (node in bindingNodes) {
                node.execute(frame)
            }

            try {
                loopBodyNode.execute(frame)
                throw IllegalStateException("Loop didn't exit properly")
            } catch (e: LoopReturnException) {
                return e.res
            }
        }
    }

    private fun emitLoop(expr: LoopExpr) =
        LoopNode(
            expr.bindings
                .map {
                    WriteLocalVarNodeGen.create(
                        emitValueNode(it.expr),
                        frameDescriptor.findOrAddFrameSlot(it.localVar))
                }
                .toTypedArray(),
            emitValueNode(expr.expr),
            expr.loc)

    class RecurNode(@Children private val recurNodes: Array<WriteLocalVarNode>, override val loc: Loc?) : ValueNode() {
        @ExplodeLoop
        override fun execute(frame: VirtualFrame): Any {
            for (node in recurNodes) {
                node.execute(frame)
            }

            throw RecurException
        }
    }

    private fun emitRecur(expr: RecurExpr) =
        RecurNode(
            expr.exprs.map {
                WriteLocalVarNodeGen.create(
                    emitValueNode(it.second),
                    frameDescriptor.findOrAddFrameSlot(it.first))
            }.toTypedArray(),
            expr.loc)

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

            is RecordExpr -> emitRecord(expr)

            is FnExpr -> emitFnExpr(expr)

            is CallExpr -> emitCallNode(expr)

            is IfExpr -> emitIf(expr)
            is DoExpr -> emitDo(expr)
            is LetExpr -> emitLet(expr)

            is LoopExpr -> emitLoop(expr)
            is RecurExpr -> emitRecur(expr)

            is LocalVarExpr -> ReadLocalVarNodeGen.create(frameDescriptor.findOrAddFrameSlot(expr.localVar))
            is GlobalVarExpr -> emitGlobalVar(expr)

            is WithFxExpr -> emitWithFx(expr)

            is CaseExpr -> emitCase(expr)
        }

    internal fun evalValueExpr(expr: ValueExpr) =
        Truffle.getRuntime().createCallTarget(makeRootNode(emitValueNode(expr))).call()!!
}
