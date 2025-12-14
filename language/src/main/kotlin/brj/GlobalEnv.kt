package brj

import brj.builtins.Builtins

class GlobalEnv(private val vars: Map<String, GlobalVar> = emptyMap()) {
    companion object {
        private val builtinFormMetas = mapOf(
            "Symbol" to GlobalVar("Symbol", SymbolMeta),
            "QualifiedSymbol" to GlobalVar("QualifiedSymbol", QualifiedSymbolMeta),
            "List" to GlobalVar("List", ListMeta),
            "Vector" to GlobalVar("Vector", VectorMeta),
            "Map" to GlobalVar("Map", MapMeta),
            "Set" to GlobalVar("Set", SetMeta),
            "Int" to GlobalVar("Int", IntMeta),
            "Double" to GlobalVar("Double", DoubleMeta),
            "String" to GlobalVar("String", StringMeta),
            "Keyword" to GlobalVar("Keyword", KeywordMeta),
            "BigInt" to GlobalVar("BigInt", BigIntMeta),
            "BigDec" to GlobalVar("BigDec", BigDecMeta),
        )

        fun withBuiltins(language: BridjeLanguage): GlobalEnv {
            val builtinFunctions = Builtins.createBuiltinFunctions(language)
            return GlobalEnv(builtinFunctions)
        }
    }

    operator fun get(name: String): GlobalVar? = vars[name] ?: builtinFormMetas[name]

    fun def(name: String, value: Any?): GlobalEnv =
        GlobalEnv(vars + (name to GlobalVar(name, value)))

    override fun toString(): String = "GlobalEnv(${vars.keys})"
}
