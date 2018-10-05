package brj

object Expr {

    class LocalVar(val s: Symbol)

    sealed class EnvUpdateExpr {
        data class DefExpr(val sym: Symbol, val params: List<LocalVar>?, val expr: ValueExpr) : EnvUpdateExpr()
    }
}