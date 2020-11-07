package brj

import brj.nodes.*

internal fun emitExpr(expr: ValueExpr): ExprNode {
    println("type ${expr.typing}")
    return when (expr) {
        is IntExpr -> IntNode(expr.int, expr.loc)
        is BoolExpr -> BoolNode(expr.bool, expr.loc)
        is StringExpr -> StringNode(expr.string, expr.loc)
        is VectorExpr -> VectorNode(expr.exprs.map(::emitExpr).toTypedArray(), expr.loc)
        else -> TODO()
    }
}

