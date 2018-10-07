package brj

data class BrjEnv(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    data class NSEnv(val vars: Map<Symbol, GlobalVar> = emptyMap()) {
        data class GlobalVar(val value: Any, val typing: Types.Typing)

        operator fun plus(newGlobalVar: Pair<Symbol, GlobalVar>): NSEnv = NSEnv(vars + newGlobalVar)
    }

    operator fun plus(newNsEnv: Pair<Symbol, NSEnv>): BrjEnv = BrjEnv(nses + newNsEnv)
}