package brj

import brj.builtins.Builtins
import brj.runtime.BridjeKey

class NsEnv(
    private val vars: Map<String, GlobalVar> = emptyMap(),
    private val keys: Map<String, BridjeKey> = emptyMap(),
) {
    companion object {
        private val builtinFormMetas = mapOf(
            "Symbol" to GlobalVar("Symbol", SymbolMeta),
            "QualifiedSymbol" to GlobalVar("QualifiedSymbol", QualifiedSymbolMeta),
            "List" to GlobalVar("List", ListMeta),
            "Vector" to GlobalVar("Vector", VectorMeta),
            "Record" to GlobalVar("Record", RecordMeta),
            "Set" to GlobalVar("Set", SetMeta),
            "Int" to GlobalVar("Int", IntMeta),
            "Double" to GlobalVar("Double", DoubleMeta),
            "String" to GlobalVar("String", StringMeta),
            "Keyword" to GlobalVar("Keyword", KeywordMeta),
            "BigInt" to GlobalVar("BigInt", BigIntMeta),
            "BigDec" to GlobalVar("BigDec", BigDecMeta),
        )

        fun withBuiltins(language: BridjeLanguage): NsEnv {
            val builtinFunctions = Builtins.createBuiltinFunctions(language)
            return NsEnv(builtinFormMetas + builtinFunctions)
        }
    }

    operator fun get(name: String): GlobalVar? = vars[name]

    fun def(name: String, value: Any?): NsEnv =
        NsEnv(vars + (name to GlobalVar(name, value)), keys)

    fun defKey(name: String, key: BridjeKey): NsEnv =
        NsEnv(vars, keys + (name to key))

    fun getKey(name: String): BridjeKey? = keys[name]

    override fun toString(): String = "NsEnv(${vars.keys})"
}
