package brj

import brj.nodes.*
import brj.nodes.CallNodeGen.CallArgsNodeGen
import brj.runtime.BridjeFunction
import brj.runtime.BridjeKey
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor

internal class ValueExprEmitter(
    private val lang: BridjeLanguage,
    private val frameDescriptor: FrameDescriptor
) {
    private fun arrayNode(exprs: List<ValueExpr>) =
        ExecuteArrayNode(lang, exprs.map { emitValueExpr(it) }.toTypedArray())

    fun emitValueExpr(expr: ValueExpr): ExprNode = when (expr) {
        is IntExpr -> IntNodeGen.create(lang, expr.int, expr.loc)
        is BoolExpr -> BoolNodeGen.create(lang, expr.bool, expr.loc)
        is StringExpr -> StringNodeGen.create(lang, expr.string, expr.loc)
        is VectorExpr -> VectorNodeGen.create(lang, arrayNode(expr.exprs), expr.loc)
        is SetExpr -> SetNodeGen.create(lang, arrayNode(expr.exprs), expr.loc)
        is RecordExpr -> RecordNodeGen.create(
            lang,
            expr.entries
                .map { RecordNodeGen.PutMemberNodeGen.create(it.key.toString(), emitValueExpr(it.value)) }
                .toTypedArray(),
            expr.loc
        )

        is KeywordExpr -> KeywordNode(lang, BridjeKey(expr.sym.toString()))

        is IfExpr -> IfNode(
            lang,
            emitValueExpr(expr.predExpr),
            emitValueExpr(expr.thenExpr),
            emitValueExpr(expr.elseExpr),
            expr.loc
        )
        is DoExpr -> DoNodeGen.create(lang, arrayNode(expr.exprs), emitValueExpr(expr.expr), expr.loc)

        is LetExpr -> LetNode(
            lang,
            expr.bindings.map {
                WriteLocalNodeGen.create(emitValueExpr(it.expr), frameDescriptor.findOrAddFrameSlot(it.binding))
            }.toTypedArray(),
            emitValueExpr(expr.expr),
            expr.loc
        )

        is FnExpr -> {
            val frameDescriptor = FrameDescriptor()

            val fnRootNode = FnRootNode(
                lang,
                frameDescriptor,
                (listOf(expr.fxLocal) + expr.params).mapIndexed { idx, localVar ->
                    WriteLocalNodeGen.create(ReadArgNode(lang, idx), frameDescriptor.findOrAddFrameSlot(localVar))
                }.toTypedArray(),
                ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr.expr)
            )

            FnNodeGen.create(lang, BridjeFunction(Truffle.getRuntime().createCallTarget(fnRootNode)), expr.loc)
        }

        is CallExpr -> CallNodeGen.create(
            lang,
            emitValueExpr(expr.fn),
            CallArgsNodeGen.create(
                emitValueExpr(expr.fxExpr),
                expr.args.map { emitValueExpr(it) }.toTypedArray()
            ),
            expr.loc
        )

        is LocalVarExpr -> LocalVarNodeGen.create(lang, frameDescriptor.findOrAddFrameSlot(expr.localVar), expr.loc)
        is GlobalVarExpr -> GlobalVarNodeGen.create(lang, expr.globalVar.bridjeVar, expr.loc)

        is WithFxExpr -> {
            WithFxNode(
                lang,
                WriteLocalNodeGen.create(
                    WithFxNode.NewFxNode(
                        lang,
                        LocalVarNodeGen.create(lang, frameDescriptor.findOrAddFrameSlot(expr.oldFx), null),
                        expr.bindings.map { WithFxNode.WithFxBindingNode(it.defxVar.sym, emitValueExpr(it.expr)) }
                            .toTypedArray()
                    ),
                    frameDescriptor.findOrAddFrameSlot(expr.newFx)
                ),
                emitValueExpr(expr.expr)
            )
        }

        is LoopExpr -> LoopNode(
            lang,
            expr.bindings.map {
                WriteLocalNodeGen.create(
                    emitValueExpr(it.expr),
                    frameDescriptor.findOrAddFrameSlot(it.binding)
                )
            }.toTypedArray(),
            emitValueExpr(expr.expr),
            expr.loc
        )

        is RecurExpr -> RecurNode(
            lang,
            expr.exprs.map {
                WriteLocalNodeGen.create(
                    emitValueExpr(it.expr),
                    frameDescriptor.findOrAddFrameSlot(it.binding)
                )
            }.toTypedArray()
        )

        is NewExpr -> {
            NewNodeGen.create(lang,
                emitValueExpr(expr.metaObj),
                arrayNode(expr.params),
                expr.loc
            )
        }
    }
}