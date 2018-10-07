package brj

data class Symbol(val name: String)
data class NamespacedSymbol(val ns: Symbol, val name: Symbol)

data class Keyword(val name: String)
data class NamespacedKeyword(val ns: Symbol, val name: Symbol)
