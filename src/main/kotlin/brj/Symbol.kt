package brj

data class Symbol(val name: String)
data class NamespacedSymbol(val ns: Symbol, val name: String)

data class Keyword(val name: String)
data class NamespacedKeyword(val ns: Symbol, val name: String)
