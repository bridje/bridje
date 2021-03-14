package brj

import brj.nodes.*
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor

internal class Emitter(
    private val lang: BridjeLanguage,
    private val frameDescriptor: FrameDescriptor = FrameDescriptor()
) {
    private fun arrayNode(exprs: List<ValueExpr>) =
        ExecuteArrayNode(exprs.map { emitValueExpr(it) }.toTypedArray())

    internal fun emitValueExpr(expr: ValueExpr): ExprNode = when (expr) {
        is IntExpr -> IntNode(expr.int, expr.loc)
        is BoolExpr -> BoolNodeGen.create(expr.bool, expr.loc)
        is StringExpr -> StringNode(expr.string, expr.loc)
        is VectorExpr -> VectorNodeGen.create(arrayNode(expr.exprs), expr.loc)
        is SetExpr -> SetNodeGen.create(arrayNode(expr.exprs), expr.loc)
        is IfExpr -> IfNode(
            emitValueExpr(expr.predExpr),
            emitValueExpr(expr.thenExpr),
            emitValueExpr(expr.elseExpr),
            expr.loc
        )
        is DoExpr -> DoNodeGen.create(arrayNode(expr.exprs), emitValueExpr(expr.expr), expr.loc)

        is LetExpr -> LetNode(
            expr.bindings.map {
                WriteLocalNode(frameDescriptor.findOrAddFrameSlot(it.binding), emitValueExpr(it.expr))
            }.toTypedArray(),
            emitValueExpr(expr.expr),
            expr.loc
        )

        is FnExpr -> {
            val frameDescriptor = FrameDescriptor()

            val fnRootNode = FnRootNode(
                lang,
                frameDescriptor,
                expr.params.map(frameDescriptor::findOrAddFrameSlot).toTypedArray(),
                Emitter(lang, frameDescriptor).emitValueExpr(expr.expr)
            )

            FnNode(BridjeFunction(Truffle.getRuntime().createCallTarget(fnRootNode)), expr.loc)
        }

        is CallExpr -> CallNodeGen.create(emitValueExpr(expr.exprs.first()), arrayNode(expr.exprs.drop(1)), expr.loc)

        is LocalVarExpr -> LocalVarNode(frameDescriptor.findOrAddFrameSlot(expr.localVar), expr.loc)
        is GlobalVarExpr -> GlobalVarNode(expr.value, expr.loc)
    }

    internal fun emitDefExpr(expr: DefExpr) =
        DefNodeGen.create(expr.sym, emitValueExpr(expr.expr), expr.loc)
}


