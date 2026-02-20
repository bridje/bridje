package brj

import brj.analyser.NsDecl
import brj.builtins.Builtins
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.Source

typealias Requires = Map<String, NsEnv>
typealias Imports = Map<String, String>

@ExportLibrary(InteropLibrary::class)
data class NsEnv(
    val requires: Requires = emptyMap(),
    val imports: Imports = emptyMap(),
    val vars: Map<String, GlobalVar> = emptyMap(),
    val keys: Map<String, GlobalVar> = emptyMap(),
    val nsDecl: NsDecl? = null,
    val source: Source? = null,
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
            "BigInt" to GlobalVar("BigInt", BigIntMeta),
            "BigDec" to GlobalVar("BigDec", BigDecMeta),
            "Keyword" to GlobalVar("Keyword", KeywordMeta),
        )

        fun withBuiltins(language: BridjeLanguage): NsEnv {
            val builtinFunctions = Builtins.createBuiltinFunctions(language)
            return NsEnv(vars = builtinFormMetas + builtinFunctions)
        }
    }

    operator fun get(name: String): GlobalVar? = vars[name]

    fun key(name: String): GlobalVar? = keys[name]

    fun def(name: String, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY): NsEnv =
        copy(vars = vars + (name to GlobalVar(name, value, meta)))

    fun defKey(name: String, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY): NsEnv =
        copy(keys = keys + (name to GlobalVar(name, value, meta)))

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
    fun getMembers(includeInternal: Boolean): Any = BridjeRecord.Keys((vars.keys + keys.keys).toTypedArray())

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String) = vars.containsKey(member) || keys.containsKey(member) || member == "__test_var_names__"

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any {
        if (member == "__test_var_names__") {
            return BridjeRecord.Keys(testVarNames().toTypedArray())
        }
        val v = vars[member] ?: keys[member] ?: throw UnknownIdentifierException.create(member)
        return v.value ?: throw UnknownIdentifierException.create(member)
    }

    @TruffleBoundary
    fun testVarNames(): List<String> =
        vars.values.filter { it.meta !== BridjeRecord.EMPTY }.mapNotNull { gv ->
            try {
                val interop = InteropLibrary.getUncached()
                if (interop.isMemberReadable(gv.meta, "test") && interop.readMember(gv.meta, "test") == true)
                    gv.name
                else null
            } catch (_: Exception) { null }
        }

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean) = "NsEnv(${vars.keys})"
}
