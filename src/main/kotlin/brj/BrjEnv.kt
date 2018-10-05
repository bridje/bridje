package brj

data class BrjEnv(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    data class NSEnv(val vars: Map<Symbol, Var> = emptyMap()) {
        data class Var(val type: Nothing, val value: Any)
    }
}