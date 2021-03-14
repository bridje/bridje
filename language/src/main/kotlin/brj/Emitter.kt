package brj

import brj.nodes.*
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor

internal class Emitter(private val lang: BridjeLanguage, private val frameDescriptor: FrameDescriptor = FrameDescriptor()) {
    internal fun emitValueExpr(expr: ValueExpr): ExprNode = when (expr) {
        is IntExpr -> IntNode(expr.int, expr.loc)
        is BoolExpr -> BoolNode(expr.bool, expr.loc)
        is StringExpr -> StringNode(expr.string, expr.loc)
        is VectorExpr -> VectorNode(expr.exprs.map(::emitValueExpr).toTypedArray(), expr.loc)
        is SetExpr -> SetNode(expr.exprs.map(::emitValueExpr).toTypedArray(), expr.loc)
        is IfExpr -> IfNode(emitValueExpr(expr.predExpr), emitValueExpr(expr.thenExpr), emitValueExpr(expr.elseExpr), expr.loc)
        is DoExpr -> DoNode(expr.exprs.map(::emitValueExpr).toTypedArray(), emitValueExpr(expr.expr), expr.loc)

        is LetExpr -> LetNode(
            expr.bindings.map {
                WriteLocalNode(frameDescriptor.findOrAddFrameSlot(it.binding), emitValueExpr(it.expr))
            }.toTypedArray(),
            emitValueExpr(expr.expr),
            expr.loc)

        is FnExpr -> {
            val frameDescriptor = FrameDescriptor()

            val fnRootNode = FnRootNode(
                lang,
                frameDescriptor,
                expr.params.map(frameDescriptor::findOrAddFrameSlot).toTypedArray(),
                emitValueExpr(expr.expr))

            FnNode(BridjeFunction(Truffle.getRuntime().createCallTarget(fnRootNode)), expr.loc)
        }

        is LocalVarExpr -> LocalVarNode(frameDescriptor.findOrAddFrameSlot(expr.localVar), expr.loc)
        is GlobalVarExpr -> GlobalVarNode(expr.value, expr.loc)
    }

    internal fun emitDefExpr(expr: DefExpr) =
        DefNodeGen.create(expr.sym, emitValueExpr(expr.expr), expr.loc)
}


