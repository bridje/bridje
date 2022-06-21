package brj.runtime

data class NsEnv(val ns: Symbol, val aliases: Map<Symbol, Symbol>, val imports: Map<Symbol, Symbol>)