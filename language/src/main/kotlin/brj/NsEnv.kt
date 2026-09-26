package brj

import brj.analyser.NsDecl
import brj.builtins.Builtins
import brj.builtins.AwaitNode
import brj.builtins.BytesCountNode
import brj.builtins.BytesFromStrNode
import brj.builtins.BytesNthNode
import brj.builtins.FileNode
import brj.builtins.FormsFromFileNode
import brj.builtins.FormsFromStringNode
import brj.builtins.FsExistsNode
import brj.builtins.FsIsDirNode
import brj.builtins.FsIsFileNode
import brj.builtins.FsListNode
import brj.builtins.FsNameNode
import brj.builtins.FsPathNode
import brj.builtins.FsReadBytesNode
import brj.builtins.FsReadStringNode
import brj.builtins.FsResolveNode
import brj.builtins.SpawnNode
import brj.builtins.StrFromBytesNode
import brj.runtime.Anomaly
import brj.runtime.BridjeFunction
import brj.runtime.BridjeKey
import brj.runtime.BridjeOptionalKey
import brj.runtime.QSymbol
import brj.runtime.BridjeRecord
import brj.runtime.FileMeta
import brj.runtime.Symbol
import brj.runtime.SymbolMeta
import brj.runtime.sym
import brj.types.*
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source

typealias Requires = Map<Symbol, NsEnv>
typealias Imports = Map<Symbol, String>

private val DECLARED_TYPE_KEY = QSymbol("brj.core".sym, "declaredType".sym)

@ExportLibrary(InteropLibrary::class)
data class NsEnv(
    val requires: Requires = emptyMap(),
    val imports: Imports = emptyMap(),
    val vars: Map<Symbol, GlobalVar> = emptyMap(),
    val keys: Map<Symbol, GlobalVar> = emptyMap(),
    val effectVars: Map<Symbol, GlobalVar> = emptyMap(),
    val interopVars: Map<Pair<Symbol, Symbol>, GlobalVar> = emptyMap(),
    val pendingDecls: Map<Symbol, Type> = emptyMap(),
    val keyTypes: Map<Symbol, Type> = emptyMap(),
    // The tags this namespace declares, by name; the checker looks them up by their runtime values.
    val tags: Map<Symbol, TagInfo> = emptyMap(),
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

        // An anomaly is a tag over a record of details; its keys, .exnMessage among them, are optional.
        private val anomalyTags = Anomaly.AnomalyMeta.entries.associate { meta ->
            Symbol.intern(meta.tag) to GlobalVar("brj.core".sym, Symbol.intern(meta.tag), meta)
        }
        private val anomalyTagInfos = Anomaly.AnomalyMeta.entries.associate { meta ->
            val name = Symbol.intern(meta.tag)
            name to TagInfo(TagRef("brj.core".sym, name), null, 0, Payload.Positional(listOf(FieldType.Fixed(RecordType(emptySet())))))
        }

        fun withBuiltins(language: BridjeLanguage): NsEnv {
            val builtinFunctions = Builtins.createBuiltinFunctions(language)
            return NsEnv(vars = builtinDataMetas + builtinFunctions + anomalyTags, tags = anomalyTagInfos)
        }

        fun withReaderBuiltins(language: BridjeLanguage): NsEnv {
            val readerNs = "brj.rdr".sym
            val formVec = VectorType(FormType)
            val fileType = TagType(TagRef("brj.fs".sym, "File".sym), emptyList())

            fun readerFn(name: String, node: RootNode, paramType: Type): Pair<Symbol, GlobalVar> {
                val sym = name.sym
                return sym to GlobalVar(readerNs, sym, BridjeFunction(node.callTarget),
                    scheme = Scheme(FnType(listOf(paramType), formVec)))
            }

            val locKeyNames = listOf("loc", "source", "path", "startLine", "startColumn", "endLine", "endColumn")
            // What a Loc's keys hold. `loc` itself is left undeclared: its value is a Loc, read by these keys.
            val locKeyTypes = mapOf(
                "source".sym to StrType, "path".sym to StrType.nullable(),
                "startLine".sym to IntType, "startColumn".sym to IntType, "endLine".sym to IntType, "endColumn".sym to IntType,
            )

            val locKeyVars = mutableMapOf<Symbol, GlobalVar>()
            val locOptVars = mutableMapOf<Symbol, GlobalVar>()
            for (name in locKeyNames) {
                val sym = name.sym
                val optSym = "?$name".sym
                val key = BridjeKey(readerNs, sym)
                val optKey = BridjeOptionalKey(key)
                locKeyVars[sym] = GlobalVar(readerNs, sym, key)
                locKeyVars[optSym] = GlobalVar(readerNs, optSym, optKey)
                locOptVars[optSym] = GlobalVar(readerNs, optSym, optKey)
            }

            return NsEnv(
                vars = mapOf(
                    "SymbolForm".sym to GlobalVar(readerNs, "SymbolForm".sym, SymbolFormMeta),
                    "QSymbolForm".sym to GlobalVar(readerNs, "QSymbolForm".sym, QSymbolFormMeta),
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
                    readerFn("fromFile", FormsFromFileNode(language), fileType),
                    readerFn("fromStr", FormsFromStringNode(language), StrType),
                ) + locOptVars,
                keys = locKeyVars,
                keyTypes = locKeyTypes,
            )
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

        private fun builtin(ns: Symbol, name: Symbol, node: RootNode, params: List<Type>, ret: Type): Pair<Symbol, GlobalVar> =
            name to GlobalVar(ns, name, BridjeFunction(node.callTarget), scheme = Scheme(FnType(params, ret)))

        fun withFsBuiltins(language: BridjeLanguage): NsEnv {
            val fsNs = "brj.fs".sym
            val file = TagType(TagRef(fsNs, "File".sym), emptyList())

            return NsEnv(vars = mapOf(
                builtin(fsNs, "file".sym, FileNode(language), listOf(StrType), file),
                builtin(fsNs, "exists".sym, FsExistsNode(language), listOf(file), BoolType),
                builtin(fsNs, "isFile".sym, FsIsFileNode(language), listOf(file), BoolType),
                builtin(fsNs, "isDir".sym, FsIsDirNode(language), listOf(file), BoolType),
                builtin(fsNs, "readString".sym, FsReadStringNode(language), listOf(file), StrType),
                builtin(fsNs, "fromBytes".sym, FsReadBytesNode(language), listOf(file), BytesType),
                builtin(fsNs, "list".sym, FsListNode(language), listOf(file), VectorType(file)),
                builtin(fsNs, "resolve".sym, FsResolveNode(language), listOf(file, StrType), file),
                builtin(fsNs, "name".sym, FsNameNode(language), listOf(file), StrType),
                builtin(fsNs, "path".sym, FsPathNode(language), listOf(file), StrType),
                "File".sym to GlobalVar(fsNs, "File".sym, FileMeta),
            ))
        }

        fun withBytesBuiltins(language: BridjeLanguage): NsEnv {
            val bytesNs = "brj.bytes".sym
            return NsEnv(vars = mapOf(
                builtin(bytesNs, "count".sym, BytesCountNode(language), listOf(BytesType), IntType),
                builtin(bytesNs, "nth".sym, BytesNthNode(language), listOf(BytesType, IntType), IntType),
                builtin(bytesNs, "fromStr".sym, BytesFromStrNode(language), listOf(StrType), BytesType),
            ))
        }

        fun withStrBuiltins(language: BridjeLanguage): NsEnv {
            val strNs = "brj.str".sym
            return NsEnv(vars = mapOf(
                builtin(strNs, "fromBytes".sym, StrFromBytesNode(language), listOf(BytesType), StrType),
            ))
        }
    }

    val nsSymbol: Symbol get() = nsDecl?.name ?: "<anonymous>".sym

    operator fun get(name: Symbol): GlobalVar? = vars[name]

    fun key(name: Symbol): GlobalVar? = keys[name]

    fun effectVar(name: Symbol): GlobalVar? = effectVars[name]

    fun defx(name: Symbol, value: Any?, scheme: Scheme, meta: BridjeRecord = BridjeRecord.EMPTY): NsEnv =
        copy(effectVars = effectVars + (name to GlobalVar(nsSymbol, name, value, meta, scheme)))

    fun decl(name: Symbol, declaredType: Type): NsEnv =
        copy(pendingDecls = pendingDecls + (name to declaredType))

    fun def(name: Symbol, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY, scheme: Scheme? = null): NsEnv {
        val declaredType = pendingDecls[name]
        val finalMeta = if (declaredType != null) meta.put(DECLARED_TYPE_KEY, TypeValue(declaredType)) else meta
        return copy(
            vars = vars + (name to GlobalVar(nsSymbol, name, value, finalMeta, scheme)),
            pendingDecls = pendingDecls - name
        )
    }

    fun withEffects(name: Symbol, effects: List<GlobalVar>): NsEnv {
        val existing = vars[name] ?: return this
        return copy(vars = vars + (name to GlobalVar(existing.ns, existing.name, existing.value, existing.meta, existing.scheme, effects)))
    }

    fun defInterop(ns: Symbol, member: Symbol, value: Any?, scheme: Scheme): NsEnv =
        copy(interopVars = interopVars + ((ns to member) to GlobalVar(ns, member, value, scheme = scheme)))

    fun interopVar(ns: Symbol, member: Symbol): GlobalVar? = interopVars[ns to member]

    fun defKey(name: Symbol, value: Any?, meta: BridjeRecord = BridjeRecord.EMPTY): NsEnv =
        copy(keys = keys + (name to GlobalVar(nsSymbol, name, value, meta)))

    fun declKeyTypes(types: Map<Symbol, Type>): NsEnv = copy(keyTypes = keyTypes + types)

    fun defTag(name: Symbol, info: TagInfo): NsEnv = copy(tags = tags + (name to info))

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
    fun getMembers(includeInternal: Boolean): Any =
        BridjeRecord.Keys((vars.keys + keys.keys + effectVars.keys).map { it.name }.toTypedArray())

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String): Boolean {
        val sym = Symbol.intern(member)
        return vars.containsKey(sym) || keys.containsKey(sym) || effectVars.containsKey(sym)
    }

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any {
        val sym = Symbol.intern(member)
        val v = vars[sym] ?: keys[sym] ?: effectVars[sym] ?: throw UnknownIdentifierException.create(member)
        return v.value ?: throw UnknownIdentifierException.create(member)
    }

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean) = "NsEnv(${vars.keys})"
}
