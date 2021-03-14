package brj

import brj.nodes.*
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor

internal class Emitter(
    private val lang: BridjeLanguage,
    private val frameDescriptor: FrameDescriptor = FrameDescriptor()
) {
    internal fun emitValueExpr(expr: ValueExpr): ExprNode = when (expr) {
        is IntExpr -> IntNode(expr.int, expr.loc)
        is BoolExpr -> BoolNode(expr.bool, expr.loc)
        is StringExpr -> StringNode(expr.string, expr.loc)
        is VectorExpr -> VectorNodeGen.create(ExecuteArrayNode(expr.exprs.map(::emitValueExpr).toTypedArray()), expr.loc)
        is SetExpr -> SetNodeGen.create(ExecuteArrayNode(expr.exprs.map(::emitValueExpr).toTypedArray()), expr.loc)
        is IfExpr -> IfNode(
            emitValueExpr(expr.predExpr),
            emitValueExpr(expr.thenExpr),
            emitValueExpr(expr.elseExpr),
            expr.loc
        )
        is DoExpr -> DoNode(expr.exprs.map(::emitValueExpr).toTypedArray(), emitValueExpr(expr.expr), expr.loc)

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

        is CallExpr -> CallNodeGen.create(
            emitValueExpr(expr.exprs.first()),
            ExecuteArrayNode(expr.exprs.drop(1).map(this::emitValueExpr).toTypedArray()),
            expr.loc
        )

        is LocalVarExpr -> LocalVarNode(frameDescriptor.findOrAddFrameSlot(expr.localVar), expr.loc)
        is GlobalVarExpr -> GlobalVarNode(expr.value, expr.loc)
    }

    internal fun emitDefExpr(expr: DefExpr) =
        DefNodeGen.create(expr.sym, emitValueExpr(expr.expr), expr.loc)
}


