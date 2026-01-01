package brj

import brj.builtins.Builtins
import brj.runtime.BridjeKey
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

typealias Requires = Map<String, NsEnv>
typealias Imports = Map<String, String>

@ExportLibrary(InteropLibrary::class)
data class NsEnv(
    val requires: Requires = emptyMap(),
    val imports: Imports = emptyMap(),
    val vars: Map<String, GlobalVar> = emptyMap(),
    val keys: Map<String, BridjeKey> = emptyMap(),
) : TruffleObject {
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
            return NsEnv(vars = builtinFormMetas + builtinFunctions)
        }
    }

    operator fun get(name: String): GlobalVar? = vars[name]

    fun def(name: String, value: Any?): NsEnv =
        copy(vars = vars + (name to GlobalVar(name, value)))

    fun defKey(name: String, key: BridjeKey): NsEnv =
        copy(keys = keys + (name to key))

    fun getKey(name: String): BridjeKey? = keys[name]

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage(): Class<BridjeLanguage> = BridjeLanguage::class.java

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean): Any = BridjeRecord.Keys(vars.keys.toTypedArray())

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String) = vars.containsKey(member)

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any {
        val v = vars[member] ?: throw UnknownIdentifierException.create(member)
        return v.value ?: throw UnknownIdentifierException.create(member)
    }

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean) = "NsEnv(${vars.keys})"
}
