package brj.nodes

import brj.*
import brj.analyser.*
import brj.effects.collectEffectfulCallees
import brj.effects.inferEffects
import brj.runtime.*
import brj.types.*
import brj.types.Nullability.NOT_NULL
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.bytecode.BytecodeConfig
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node.Child
import com.oracle.truffle.api.nodes.Node.Children
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source

class ParseRootNode(
    private val lang: BridjeLanguage,
    private val nsDecl: NsDecl?,
    private val forms: List<Form>,
    private val source: Source,
    @field:Children private val requires: Array<RequireNsNode>
) : RootNode(lang) {

    @ExplodeLoop
    private fun resolveRequires(frame: VirtualFrame): Requires {
        val result = mutableMapOf<Symbol, NsEnv>()
        for (node in requires) {
            result[node.alias] = node.execute(frame)
        }
        return result
    }

    private fun NsDecl.resolve(frame: VirtualFrame): NsEnv =
        NsEnv(
            requires = resolveRequires(frame),
            imports = this.imports,
            nsDecl = this,
            source = source
        )

    /**
     * Builds one bytecode unit, wrapped in this namespace's [Source] so that every root emitted
     * inside it can record source sections.
     */
    private fun buildRoot(emit: (Builder) -> BridjeRootNode): BridjeRootNode {
        lateinit var root: BridjeRootNode
        BridjeRootNodeGen.create(lang, BytecodeConfig.WITH_SOURCE) { b ->
            b.beginSource(source)
            root = emit(b)
            b.endSource()
        }
        return root
    }

    @TruffleBoundary
    private fun evalExpr(expr: ValueExpr, slotCount: Int): Any? {
        val ctx = BridjeContext.get(this)
        val root = buildRoot { b ->
            b.beginRoot()
            val emitter = Emitter(lang, ctx, b, source, slotCount)
            b.beginReturn()
            emitter.emitExpr(expr)
            b.endReturn()
            b.endRoot()
        }
        return root.callTarget.call()
    }

    @TruffleBoundary
    private fun evalEffectfulDef(fnExpr: FnExpr): Any {
        val ctx = BridjeContext.get(this)
        val root = buildRoot { b -> Emitter(lang, ctx, b, source, 0).emitEffectfulDefRoot(fnExpr) }
        return BridjeFunction(root.callTarget)
    }

    private fun locMeta(expr: Expr): BridjeRecord =
        expr.loc?.let { BridjeRecord.EMPTY.put(LOC_KEY, Loc(it)) } ?: BridjeRecord.EMPTY

    private fun evalDefTag(expr: DefTagExpr, nsEnv: NsEnv, enumName: Symbol? = null): Pair<Any, NsEnv> {
        val ns = nsEnv.nsSymbol
        val qFieldNames = expr.fieldNames.map { QSymbol(ns, it) }

        val value: Any =
            if (expr.fieldNames.isEmpty()) {
                BridjeTaggedSingleton(expr.name.name)
            } else {
                BridjeTagConstructor(expr.name.name, expr.fieldNames.size, qFieldNames)
            }

        val type = if (expr.fieldNames.isEmpty()) {
            if (enumName != null && expr.typeVarNames.isNotEmpty()) {
                // Nullary variant of a parameterised enum (e.g., Nothing in Maybe(a))
                // needs fresh type vars so it can unify with any instantiation.
                val variances = expr.typeVarNames.map { Variance.INVARIANT }
                val freshArgs = expr.typeVarNames.map { freshType() }
                EnumType(enumName, freshArgs, variances).notNull()
            } else if (enumName != null) {
                EnumType(enumName).notNull()
            } else {
                TagType(ns, expr.name).notNull()
            }
        } else {
            val typeVars = expr.typeVarNames.associateWith { TypeVar() }
            val variances = expr.typeVarNames.map { Variance.INVARIANT }
            val fieldTypes = expr.fieldNames.map { fieldName ->
                if (fieldName.name in typeVars) Type(NOT_NULL, typeVars[fieldName.name]!!, null)
                else freshType()
            }
            val tagArgs = typeVars.values.map { Type(NOT_NULL, it, null) }
            val returnType = if (enumName != null) {
                EnumType(enumName, tagArgs, variances)
            } else {
                TagType(ns, expr.name, tagArgs, variances)
            }
            FnType(fieldTypes, returnType.notNull()).notNull()
        }

        var updatedNs = nsEnv.def(expr.name, value, meta = locMeta(expr), type = type)

        if (expr.recordStyle) {
            // tag: Foo({:k1, :k2}) — register each field name as a key as well.
            for (fieldSym in expr.fieldNames) {
                val key = BridjeKey(ns, fieldSym)
                val optKey = BridjeOptionalKey(key)
                val keyType = FnType(listOf(RecordType.notNull()), freshType()).notNull()
                val optKeyType = FnType(listOf(RecordType.notNull()), freshType()).notNull()
                val optName = Symbol.intern("?$fieldSym")
                updatedNs = updatedNs.defKey(fieldSym, key, type = keyType)
                updatedNs = updatedNs.defKey(optName, optKey, type = optKeyType)
                updatedNs = updatedNs.def(optName, optKey, type = optKeyType)
            }
        }

        return value to updatedNs
    }

    @TruffleBoundary
    private fun List<Form>.evalForms(ctx: BridjeContext, nsEnv: NsEnv): Pair<NsEnv, Any?> {
        var nsEnv = nsEnv
        val errors = mutableListOf<Analyser.Error>()

        val res = fold(null as Any?) { _, form ->
            val analyser = Analyser(ctx, nsEnv)

            when (val expr = analyser.analyse(form)) {
                is TopLevelDo -> {
                    val (newNsEnv, res) = expr.forms.evalForms(ctx, nsEnv)
                    nsEnv = newNsEnv
                    res
                }

                is DefExpr -> {
                    val type = expr.valueExpr.checkType()
                    val effects = expr.valueExpr.inferEffects().toList()
                    val userMeta = expr.metaExpr?.let { evalExpr(it, analyser.slotCount) as? BridjeRecord } ?: BridjeRecord.EMPTY
                    val meta = expr.loc?.let { userMeta.put(LOC_KEY, Loc(it)) } ?: userMeta

                    if (effects.isNotEmpty()) {
                        if (expr.valueExpr !is FnExpr) {
                            throw Analyser.Error("effects can only be used within a function body: ${expr.name}", expr.loc)
                        }
                        val value = evalEffectfulDef(expr.valueExpr)
                        nsEnv = nsEnv.def(expr.name, value, meta, type).withEffects(expr.name, effects)
                        value
                    } else {
                        val value = evalExpr(expr.valueExpr, analyser.slotCount)
                        nsEnv = nsEnv.def(expr.name, value, meta, type)
                        value
                    }
                }

                is DefTagExpr -> {
                    val (value, updatedNsEnv) = evalDefTag(expr, nsEnv)
                    nsEnv = updatedNsEnv
                    value
                }

                is DefEnumExpr -> {
                    val variantNames = mutableSetOf<Symbol>()
                    var lastValue: Any? = null
                    for (tagExpr in expr.variants) {
                        val (value, updatedNsEnv) = evalDefTag(tagExpr, nsEnv, enumName = expr.name)
                        nsEnv = updatedNsEnv
                        variantNames.add(tagExpr.name)
                        lastValue = value
                    }
                    nsEnv = nsEnv.defEnum(expr.name, variantNames)
                    lastValue
                }

                is DefMacroExpr -> {
                    val type = expr.fn.checkType()
                    val fnType = type.base as? FnType
                        ?: throw TypeErrorException("defmacro body did not produce a function type: $type")
                    val formType = FormType.notNull()
                    val formConstraints = fnType.paramTypes.mapIndexed { i, paramType ->
                        val isRest = expr.fn.isVariadic && i == fnType.paramTypes.lastIndex
                        val expected = if (isRest) VectorType(formType).notNull() else formType
                        expected subOf paramType
                    } + (fnType.returnType subOf formType)
                    formConstraints.resolve()

                    val fn = evalExpr(expr.fn, analyser.slotCount)
                    val fixedArity = if (expr.fn.isVariadic) expr.fn.params.size - 1 else expr.fn.params.size
                    val macro = BridjeMacro(fn!!, fixedArity, expr.fn.isVariadic)
                    nsEnv = nsEnv.def(expr.name, macro, meta = locMeta(expr), type = type)
                    macro
                }

                is DeclExpr -> {
                    nsEnv = nsEnv.decl(expr.name, expr.declaredType)
                    null
                }

                is InteropDeclExpr -> {
                    val interopLib = InteropLibrary.getUncached()
                    for (member in expr.members) {
                        val fqClass = nsEnv.imports[member.importAlias]
                            ?: throw Analyser.Error("Unknown import alias: ${member.importAlias}", expr.loc)
                        val hostClass = ctx.truffleEnv.lookupHostSymbol(fqClass) as TruffleObject
                        val memberName = member.memberName.name

                        when (member.kind) {
                            InteropMemberKind.STATIC_FIELD -> {
                                if (!interopLib.isMemberReadable(hostClass, memberName)) {
                                    throw Analyser.Error("$memberName is not a readable field on ${member.importAlias} — did you mean $memberName()?", expr.loc)
                                }
                                val value = interopLib.readMember(hostClass, memberName)
                                nsEnv = nsEnv.defInterop(member.importAlias, member.memberName, value, member.declaredType)
                            }
                            InteropMemberKind.STATIC_METHOD -> {
                                val rootNode = if (memberName == "new")
                                    HostConstructorNode(lang, hostClass)
                                else
                                    HostStaticMethodInvokeNode(lang, hostClass, memberName)
                                nsEnv = nsEnv.defInterop(member.importAlias, member.memberName, BridjeFunction(rootNode.callTarget), member.declaredType)
                            }
                            InteropMemberKind.INSTANCE_METHOD -> {
                                val rootNode = HostInstanceMethodInvokeNode(lang, memberName)
                                nsEnv = nsEnv.defInterop(member.importAlias, member.memberName, BridjeFunction(rootNode.callTarget), member.declaredType)
                            }
                            InteropMemberKind.INSTANCE_FIELD -> {
                                val rootNode = HostInstanceFieldReadNode(lang, memberName)
                                nsEnv = nsEnv.defInterop(member.importAlias, member.memberName, BridjeFunction(rootNode.callTarget), member.declaredType)
                            }
                        }
                    }
                    null
                }

                is DefxExpr -> {
                    val defaultValue = expr.defaultExpr?.let { evalExpr(it, analyser.slotCount) }
                    nsEnv = nsEnv.defx(expr.name, defaultValue, expr.declaredType, meta = locMeta(expr))
                    defaultValue
                }

                is DefKeysExpr -> {
                    val nsSym = nsEnv.nsSymbol
                    var lastKey: BridjeKey? = null
                    for (name in expr.names) {
                        val key = BridjeKey(nsSym, name)
                        val optKey = BridjeOptionalKey(key)
                        val keyType = FnType(listOf(RecordType.notNull()), freshType()).notNull()
                        val optKeyType = FnType(listOf(RecordType.notNull()), freshType()).notNull()
                        val optName = Symbol.intern("?$name")
                        nsEnv = nsEnv.defKey(name, key, type = keyType)
                        nsEnv = nsEnv.defKey(optName, optKey, type = optKeyType)
                        nsEnv = nsEnv.def(optName, optKey, type = optKeyType)
                        lastKey = key
                    }
                    lastKey
                }

                is ValueExpr -> {
                    expr.checkType()
                    evalExpr(expr, analyser.slotCount)
                }

                is AnalyserErrors -> {
                    errors.addAll(expr.errors)
                    null
                }
            }
        }

        return when {
            errors.isEmpty() -> Pair(nsEnv, res)
            else -> throw Analyser.Errors(errors)
        }
    }

    @TruffleBoundary
    private fun doExecute(initialNsEnv: NsEnv): Any? {
        val ctx = BridjeContext.get(this)

        val (nsEnv, result) = forms.evalForms(ctx, initialNsEnv)

        if (nsDecl == null) return result

        ctx.updateGlobalEnv { globalEnv ->
            val invalidated = globalEnv.invalidateNamespace(nsDecl.name)
            invalidated.withNamespace(nsDecl.name, nsEnv)
        }

        // Update brjCore if this is brj:core namespace
        if (nsDecl.name == "brj.core".sym) {
            ctx.brjCore = nsEnv
        }

        return nsEnv
    }

    override fun execute(frame: VirtualFrame): Any? {
        val initialNsEnv = when {
            // brj:core starts with Kotlin builtins
            nsDecl?.name == "brj.core".sym -> NsEnv.withBuiltins(lang).copy(
                requires = resolveRequires(frame),
                imports = nsDecl.imports,
                nsDecl = nsDecl,
                source = source
            )
            // brj:concurrent starts with spawn as a Kotlin builtin
            nsDecl?.name == "brj.concurrent".sym -> NsEnv.withConcurrentBuiltins(lang).let { base ->
                base.copy(
                    requires = resolveRequires(frame),
                    imports = nsDecl.imports,
                    nsDecl = nsDecl,
                    source = source
                )
            }
            // brj:fs starts with file as a Kotlin builtin and File as the tag
            nsDecl?.name == "brj.fs".sym -> NsEnv.withFsBuiltins(lang).let { base ->
                base.copy(
                    requires = resolveRequires(frame),
                    imports = nsDecl.imports,
                    nsDecl = nsDecl,
                    source = source
                )
            }
            // brj:bytes starts with Bytes interop builtins
            nsDecl?.name == "brj.bytes".sym -> NsEnv.withBytesBuiltins(lang).let { base ->
                base.copy(
                    requires = resolveRequires(frame),
                    imports = nsDecl.imports,
                    nsDecl = nsDecl,
                    source = source
                )
            }
            // brj:str starts with Str interop builtins
            nsDecl?.name == "brj.str".sym -> NsEnv.withStrBuiltins(lang).let { base ->
                base.copy(
                    requires = resolveRequires(frame),
                    imports = nsDecl.imports,
                    nsDecl = nsDecl,
                    source = source
                )
            }
            nsDecl != null -> nsDecl.resolve(frame)
            else -> NsEnv()
        }
        return doExecute(initialNsEnv)
    }
}
