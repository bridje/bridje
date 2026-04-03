package brj

import brj.analyser.NsDecl
import brj.builtins.Builtins
import brj.runtime.Anomaly
import brj.runtime.BridjeRecord
import brj.types.FnType
import brj.types.RecordType
import brj.types.TagType
import brj.types.Type
import brj.types.notNull
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
    val effectVars: Map<String, GlobalVar> = emptyMap(),
    val pendingDecls: Map<String, Type> = emptyMap(),
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

        private val anomalyTags = Anomaly.AnomalyMeta.entries.associate { meta ->
            val tagType = TagType("brj.core", meta.tag)
            val type = FnType(listOf(RecordType.notNull()), tagType.notNull()).notNull()
            meta.tag to GlobalVar(meta.tag, meta, type = type)
        }

        fun withBuiltins(language: BridjeLanguage): NsEnv {
            val builtinFunctions = Builtins.createBuiltinFunctions(language)
            return NsEnv(vars = builtinFormMetas + builtinFunctions + anomalyTags)
        }
    }

    operator fun get(name: String): GlobalVar? = vars[name]

    fun key(name: String): GlobalVar? = keys[name]

    fun effectVar(name: String): GlobalVar? = effectVars[name]

    fun defx(name: String, value: Any?, type: Type): NsEnv =
        copy(effectVars = effectVars + (name to GlobalVar(name, value, type = type)))

    fun decl(name: String, declaredType: Type): NsEnv =
        copy(pendingDecls = pendingDecls + (name to declaredType))

    fun def(name: String, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY, type: Type? = null): NsEnv {
        val declaredType = pendingDecls[name]
        val finalMeta = if (declaredType != null) meta.put("declaredType", declaredType) else meta
        return copy(
            vars = vars + (name to GlobalVar(name, value, finalMeta, type)),
            pendingDecls = pendingDecls - name
        )
    }

    fun withEffects(name: String, effects: List<GlobalVar>): NsEnv {
        val existing = vars[name] ?: return this
        return copy(vars = vars + (name to GlobalVar(existing.name, existing.value, existing.meta, existing.type, effects)))
    }

    fun defKey(name: String, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY, type: Type? = null): NsEnv =
        copy(keys = keys + (name to GlobalVar(name, value, meta, type)))

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
    fun getMembers(includeInternal: Boolean): Any = BridjeRecord.Keys((vars.keys + keys.keys + effectVars.keys).toTypedArray())

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String) =
        vars.containsKey(member) || keys.containsKey(member) || effectVars.containsKey(member)
            || member == "__test_var_names__"
            || member.startsWith("__var_meta__:")

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any {
        if (member == "__test_var_names__") {
            return BridjeRecord.Keys(testVarNames().toTypedArray())
        }
        if (member.startsWith("__var_meta__:")) {
            val varName = member.removePrefix("__var_meta__:")
            val gv = vars[varName] ?: throw UnknownIdentifierException.create(member)
            return gv.meta
        }
        val v = vars[member] ?: keys[member] ?: effectVars[member] ?: throw UnknownIdentifierException.create(member)
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
