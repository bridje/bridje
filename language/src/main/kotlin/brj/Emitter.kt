package brj

import brj.nodes.*

internal fun emitExpr(expr: ValueExpr) = when (expr) {
    is IntExpr -> IntNode(expr.int, expr.loc)
    is BoolExpr -> BoolNode(expr.bool, expr.loc)
    is StringExpr -> StringNode(expr.string, expr.loc)
    else -> TODO()
}

