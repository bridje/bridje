package brj.analyser

import brj.runtime.Symbol

data class NsDecl(
    val name: Symbol,
    val requires: Map<Symbol, Symbol> = emptyMap(),
    val imports: Map<Symbol, String> = emptyMap()
)

