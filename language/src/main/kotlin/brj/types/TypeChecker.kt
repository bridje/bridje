package brj.types

import brj.analyser.ValueExpr

object TypeChecker {
    val enabled: Boolean = System.getenv("BRIDJE_CHECK_TYPES")?.let {
        it == "true" || it == "1"
    } ?: false

    fun checkIfEnabled(expr: ValueExpr): Type? =
        if (enabled) expr.checkType() else null
}
