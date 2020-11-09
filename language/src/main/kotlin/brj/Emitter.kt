package brj

import brj.nodes.*
import com.oracle.truffle.api.frame.FrameDescriptor

internal class Emitter(private val frameDescriptor: FrameDescriptor = FrameDescriptor()) {
    internal fun emitExpr(expr: ValueExpr): ExprNode = when (expr) {
        is IntExpr -> IntNode(expr.int, expr.loc)
        is BoolExpr -> BoolNode(expr.bool, expr.loc)
        is StringExpr -> StringNode(expr.string, expr.loc)
        is VectorExpr -> VectorNode(expr.exprs.map(::emitExpr).toTypedArray(), expr.loc)
        is SetExpr -> SetNode(expr.exprs.map(::emitExpr).toTypedArray(), expr.loc)
        is IfExpr -> IfNode(emitExpr(expr.predExpr), emitExpr(expr.thenExpr), emitExpr(expr.elseExpr), expr.loc)
        is DoExpr -> DoNode(expr.exprs.map(::emitExpr).toTypedArray(), emitExpr(expr.expr), expr.loc)
        is LetExpr -> LetNode(
            expr.bindings.map {
                WriteLocalNode(frameDescriptor.findOrAddFrameSlot(it.binding), emitExpr(it.expr))
            }.toTypedArray(),
            emitExpr(expr.expr),
            expr.loc)
        is LocalVarExpr -> LocalVarNode(frameDescriptor.findOrAddFrameSlot(expr.localVar), expr.loc)
    }
}


