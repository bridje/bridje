package brj

import brj.analyser.NsDecl
import brj.builtins.Builtins
import brj.builtins.AwaitNode
import brj.builtins.FileNode
import brj.builtins.FormsFromFileNode
import brj.builtins.FormsFromStringNode
import brj.builtins.FsExistsNode
import brj.builtins.FsIsDirNode
import brj.builtins.FsIsFileNode
import brj.builtins.FsListNode
import brj.builtins.FsNameNode
import brj.builtins.FsPathNode
import brj.builtins.FsReadStringNode
import brj.builtins.FsResolveNode
import brj.builtins.SpawnNode
import brj.runtime.Anomaly
import brj.runtime.BridjeFunction
import brj.runtime.BridjeRecord
import brj.runtime.FileMeta
import brj.runtime.Symbol
import brj.runtime.SymbolMeta
import brj.runtime.sym
import brj.types.BoolType
import brj.types.FnType
import brj.types.FormType
import brj.types.RecordType
import brj.types.StringType
import brj.types.TagType
import brj.types.Type
import brj.types.VectorType
import brj.types.freshType
import brj.types.notNull
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source

typealias Requires = Map<String, NsEnv>
typealias Imports = Map<String, String>

@ExportLibrary(InteropLibrary::class)
data class NsEnv(
    val requires: Requires = emptyMap(),
    val imports: Imports = emptyMap(),
    val vars: Map<Symbol, GlobalVar> = emptyMap(),
    val keys: Map<Symbol, GlobalVar> = emptyMap(),
    val effectVars: Map<Symbol, GlobalVar> = emptyMap(),
    val interopVars: Map<String, GlobalVar> = emptyMap(),
    val pendingDecls: Map<Symbol, Type> = emptyMap(),
    val enums: Map<Symbol, Set<Symbol>> = emptyMap(),
    val nsDecl: NsDecl? = null,
    val source: Source? = null,
) : TruffleObject {
    companion object {
        private val builtinDataMetas = run {
            val coreNs = "brj.core".sym
            mapOf(
                "Symbol".sym to GlobalVar(coreNs, "Symbol".sym, SymbolMeta),
                "Var".sym to GlobalVar(coreNs, "Var".sym, VarMeta),
            )
        }

        private val anomalyTags = Anomaly.AnomalyMeta.entries.associate { meta ->
            val tagType = TagType("brj.core", meta.tag)
            val type = FnType(listOf(RecordType.notNull()), tagType.notNull()).notNull()
            Symbol.intern(meta.tag) to GlobalVar("brj.core".sym, Symbol.intern(meta.tag), meta, type = type)
        }

        fun withBuiltins(language: BridjeLanguage): NsEnv {
            val builtinFunctions = Builtins.createBuiltinFunctions(language)
            return NsEnv(vars = builtinDataMetas + builtinFunctions + anomalyTags)
        }

        fun withReaderBuiltins(language: BridjeLanguage): NsEnv {
            val readerNs = "brj.rdr".sym
            val formVec = VectorType(FormType.notNull()).notNull()
            val fileType = TagType("brj.fs", "File").notNull()
            val str = StringType.notNull()

            fun readerFn(name: String, node: RootNode, paramType: Type): Pair<Symbol, GlobalVar> {
                val sym = name.sym
                return sym to GlobalVar(readerNs, sym, BridjeFunction(node.callTarget),
                    type = FnType(listOf(paramType), formVec).notNull())
            }

            return NsEnv(vars = mapOf(
                "SymbolForm".sym to GlobalVar(readerNs, "SymbolForm".sym, SymbolFormMeta),
                "QSymbolForm".sym to GlobalVar(readerNs, "QSymbolForm".sym, QSymbolFormMeta),
                "KeywordForm".sym to GlobalVar(readerNs, "KeywordForm".sym, KeywordFormMeta),
                "QKeywordForm".sym to GlobalVar(readerNs, "QKeywordForm".sym, QKeywordFormMeta),
                "DotSymbolForm".sym to GlobalVar(readerNs, "DotSymbolForm".sym, DotSymbolFormMeta),
                "QDotSymbolForm".sym to GlobalVar(readerNs, "QDotSymbolForm".sym, QDotSymbolFormMeta),
                "List".sym to GlobalVar(readerNs, "List".sym, ListMeta),
                "Vector".sym to GlobalVar(readerNs, "Vector".sym, VectorMeta),
                "Record".sym to GlobalVar(readerNs, "Record".sym, RecordMeta),
                "Set".sym to GlobalVar(readerNs, "Set".sym, SetMeta),
                "Int".sym to GlobalVar(readerNs, "Int".sym, IntMeta),
                "Double".sym to GlobalVar(readerNs, "Double".sym, DoubleMeta),
                "String".sym to GlobalVar(readerNs, "String".sym, StringMeta),
                "BigInt".sym to GlobalVar(readerNs, "BigInt".sym, BigIntMeta),
                "BigDec".sym to GlobalVar(readerNs, "BigDec".sym, BigDecMeta),
                "Unquote".sym to GlobalVar(readerNs, "Unquote".sym, UnquoteMeta),
                "UnquoteSplice".sym to GlobalVar(readerNs, "UnquoteSplice".sym, UnquoteSpliceMeta),
                "SyntaxQuote".sym to GlobalVar(readerNs, "SyntaxQuote".sym, SyntaxQuoteMeta),
                readerFn("<-file", FormsFromFileNode(language), fileType),
                readerFn("<-str", FormsFromStringNode(language), str),
            ))
        }

        fun withConcurrentBuiltins(language: BridjeLanguage): NsEnv {
            val spawnFn = BridjeFunction(SpawnNode(language).callTarget)
            val awaitFn = BridjeFunction(AwaitNode(language).callTarget)
            val concurrentNs = "brj.concurrent".sym
            return NsEnv(vars = mapOf(
                "spawn".sym to GlobalVar(concurrentNs, "spawn".sym, spawnFn),
                "await".sym to GlobalVar(concurrentNs, "await".sym, awaitFn),
            ))
        }

        fun withFsBuiltins(language: BridjeLanguage): NsEnv {
            val fsNs = "brj.fs".sym
            val fileTagType = TagType("brj.fs", "File").notNull()
            val str = StringType.notNull()
            val bool = BoolType.notNull()

            fun gv(name: Symbol, node: RootNode, params: List<Type>, ret: Type): Pair<Symbol, GlobalVar> =
                name to GlobalVar(fsNs, name, BridjeFunction(node.callTarget),
                    type = FnType(params, ret).notNull())

            val fileCtorType = FnType(listOf(freshType()), fileTagType).notNull()
            return NsEnv(vars = mapOf(
                gv("file".sym, FileNode(language), listOf(str), fileTagType),
                gv("exists?".sym, FsExistsNode(language), listOf(fileTagType), bool),
                gv("isFile?".sym, FsIsFileNode(language), listOf(fileTagType), bool),
                gv("isDir?".sym, FsIsDirNode(language), listOf(fileTagType), bool),
                gv("readString".sym, FsReadStringNode(language), listOf(fileTagType), str),
                gv("list".sym, FsListNode(language), listOf(fileTagType), VectorType(fileTagType).notNull()),
                gv("resolve".sym, FsResolveNode(language), listOf(fileTagType, str), fileTagType),
                gv("name".sym, FsNameNode(language), listOf(fileTagType), str),
                gv("path".sym, FsPathNode(language), listOf(fileTagType), str),
                "File".sym to GlobalVar(fsNs, "File".sym, FileMeta, type = fileCtorType),
            ))
        }
    }

    private val nsSymbol: Symbol get() = (nsDecl?.name ?: "<anonymous>").sym

    operator fun get(name: Symbol): GlobalVar? = vars[name]

    fun key(name: Symbol): GlobalVar? = keys[name]

    fun effectVar(name: Symbol): GlobalVar? = effectVars[name]

    fun defx(name: Symbol, value: Any?, type: Type, meta: BridjeRecord = BridjeRecord.EMPTY): NsEnv =
        copy(effectVars = effectVars + (name to GlobalVar(nsSymbol, name, value, meta, type)))

    fun decl(name: Symbol, declaredType: Type): NsEnv =
        copy(pendingDecls = pendingDecls + (name to declaredType))

    fun def(name: Symbol, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY, type: Type? = null): NsEnv {
        val declaredType = pendingDecls[name]
        val finalMeta = if (declaredType != null) meta.put("declaredType", declaredType) else meta
        return copy(
            vars = vars + (name to GlobalVar(nsSymbol, name, value, finalMeta, type)),
            pendingDecls = pendingDecls - name
        )
    }

    fun withEffects(name: Symbol, effects: List<GlobalVar>): NsEnv {
        val existing = vars[name] ?: return this
        return copy(vars = vars + (name to GlobalVar(existing.ns, existing.name, existing.value, existing.meta, existing.type, effects)))
    }

    fun defInterop(qualifiedName: String, value: Any?, type: Type): NsEnv =
        copy(interopVars = interopVars + (qualifiedName to GlobalVar(nsSymbol, Symbol.intern(qualifiedName), value, type = type)))

    fun interopVar(qualifiedName: String): GlobalVar? = interopVars[qualifiedName]

    fun defKey(name: Symbol, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY, type: Type? = null): NsEnv =
        copy(keys = keys + (name to GlobalVar(nsSymbol, name, value, meta, type)))

    fun defEnum(enumName: Symbol, variantNames: Set<Symbol>): NsEnv =
        copy(enums = enums + (enumName to variantNames))

    fun enumForTag(tagName: Symbol): Symbol? = enums.entries.firstOrNull { tagName in it.value }?.key

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
    fun getMembers(includeInternal: Boolean): Any = BridjeRecord.Keys((vars.keys + keys.keys + effectVars.keys).map { it.name }.toTypedArray())

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String) =
        vars.containsKey(Symbol.intern(member)) || keys.containsKey(Symbol.intern(member)) || effectVars.containsKey(Symbol.intern(member))
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
            val gv = vars[Symbol.intern(varName)] ?: throw UnknownIdentifierException.create(member)
            return gv.meta
        }
        val v = vars[Symbol.intern(member)] ?: keys[Symbol.intern(member)] ?: effectVars[Symbol.intern(member)] ?: throw UnknownIdentifierException.create(member)
        return v.value ?: throw UnknownIdentifierException.create(member)
    }

    @TruffleBoundary
    fun testVarNames(): List<String> =
        vars.values.filter { it.meta !== BridjeRecord.EMPTY }.mapNotNull { gv ->
            try {
                val interop = InteropLibrary.getUncached()
                if (interop.isMemberReadable(gv.meta, "test") && interop.readMember(gv.meta, "test") == true)
                    gv.name.name
                else null
            } catch (_: Exception) { null }
        }

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean) = "NsEnv(${vars.keys})"
}
