package brj.nodes

import brj.*
import brj.analyser.*
import brj.effects.collectEffectfulCallees
import brj.effects.inferEffects
import brj.runtime.*
import brj.types.*
import brj.types.Nullability.NOT_NULL
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
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
        val result = mutableMapOf<String, NsEnv>()
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

    private fun buildFrameDescriptor(slotCount: Int): FrameDescriptor {
        val builder = FrameDescriptor.newBuilder()
        repeat(slotCount) {
            builder.addSlot(FrameSlotKind.Illegal, null, null)
        }
        return builder.build()
    }

    private class EvalNode(
        lang: BridjeLanguage,
        frameDescriptor: FrameDescriptor,
        @field:Child private var node: BridjeNode
    ) : RootNode(lang, frameDescriptor) {
        override fun execute(frame: VirtualFrame) = node.execute(frame)
    }

    @TruffleBoundary
    private fun evalExpr(expr: ValueExpr, slotCount: Int): Any? {
        val emitter = Emitter(lang)
        emitter.nextSlot = slotCount
        val node = emitter.emitExpr(expr)
        val frameDescriptor = buildFrameDescriptor(emitter.nextSlot)
        return EvalNode(lang, frameDescriptor, node).callTarget.call(this)
    }

    @TruffleBoundary
    private fun evalEffectfulDef(fnExpr: FnExpr, effects: List<GlobalVar>, slotCount: Int): Any? {
        // Two-stage function: outer fn takes fx map, returns inner fn.
        //
        // Outer fn frame layout:
        //   slot 0          — fx map (the single argument)
        //   slot 1..N       — pre-applied effectful callees
        //
        // The outer fn body: pre-applies callees from the fx map, then creates the inner fn.
        // The inner fn captures the fx map and pre-applied callees.

        val fxSlot = 0
        val emitter = Emitter(lang)

        // Find effectful callees in the body to pre-apply.
        val callees = fnExpr.bodyExpr.collectEffectfulCallees().toList()

        // Allocate outer frame slots for pre-applied callees starting at slot 1.
        val calleeSlots = callees.mapIndexed { idx, gv -> gv to (1 + idx) }
        val outerSlotCount = 1 + callees.size

        // Inner fn captures: [0] = fx map, [1..N] = pre-applied callees.
        val innerFxSource: NodeSource = CapturedNodeSource(0)
        val preApplied = callees.mapIndexed { idx, gv ->
            gv to CapturedNodeSource(1 + idx) as NodeSource
        }.toMap()

        // Emit inner fn body with fx map and pre-applied callees available via captures.
        emitter.nextSlot = fnExpr.slotCount
        val innerBodyNode = emitter.emitExpr(fnExpr.bodyExpr, innerFxSource, preApplied)

        val innerFdBuilder = FrameDescriptor.newBuilder()
        repeat(emitter.nextSlot) { innerFdBuilder.addSlot(FrameSlotKind.Illegal, null, null) }
        val innerRoot = FnRootNode(lang, innerFdBuilder.build(), fnExpr.params.size, true, innerBodyNode)

        // Capture fx map + pre-applied callees from outer frame.
        val captureSources = (0 until outerSlotCount).map {
            FrameSlotCapture(it) as CaptureSource
        }.toTypedArray()
        val innerFnNode = ClosureFnNode(innerRoot.callTarget, captureSources, fnExpr.loc)

        // Outer fn body: pre-apply each callee, then create the inner fn.
        var outerBodyNode: BridjeNode = innerFnNode
        for ((callee, slot) in calleeSlots.reversed()) {
            val preApplyNode = InvokeNode(
                GlobalVarNode(callee, fnExpr.loc),
                arrayOf(ReadLocalNode(fxSlot, fnExpr.loc) as BridjeNode),
                fnExpr.loc
            )
            outerBodyNode = LetNode(slot, preApplyNode, outerBodyNode, fnExpr.loc)
        }

        // Outer fn: takes 1 param (the fx map), returns the inner fn closure.
        val outerFdBuilder = FrameDescriptor.newBuilder()
        repeat(outerSlotCount) { outerFdBuilder.addSlot(FrameSlotKind.Illegal, null, null) }
        val outerRoot = FnRootNode(lang, outerFdBuilder.build(), 1, false, outerBodyNode)

        return BridjeFunction(outerRoot.callTarget)
    }

    private fun locMeta(expr: Expr): BridjeRecord =
        expr.loc?.let { BridjeRecord.EMPTY.put("loc", Loc(it)) } ?: BridjeRecord.EMPTY

    private fun evalDefTag(expr: DefTagExpr, nsEnv: NsEnv, enumName: String? = null): Pair<Any, NsEnv> {
        val value: Any =
            if (expr.fieldNames.isEmpty()) {
                BridjeTaggedSingleton(expr.name.name)
            } else {
                BridjeTagConstructor(expr.name.name, expr.fieldNames.size, expr.fieldNames)
            }

        val ns = nsEnv.nsDecl?.name ?: ""
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
                TagType(ns, expr.name.name).notNull()
            }
        } else {
            val typeVars = expr.typeVarNames.associateWith { TypeVar() }
            val variances = expr.typeVarNames.map { Variance.INVARIANT }
            val fieldTypes = expr.fieldNames.map { fieldName ->
                if (fieldName in typeVars) Type(NOT_NULL, typeVars[fieldName]!!, null)
                else freshType()
            }
            val tagArgs = typeVars.values.map { Type(NOT_NULL, it, null) }
            val returnType = if (enumName != null) {
                EnumType(enumName, tagArgs, variances)
            } else {
                TagType(ns, expr.name.name, tagArgs, variances)
            }
            FnType(fieldTypes, returnType.notNull()).notNull()
        }

        return value to nsEnv.def(expr.name, value, meta = locMeta(expr), type = type)
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
                    val meta = expr.loc?.let { userMeta.put("loc", Loc(it)) } ?: userMeta

                    if (effects.isNotEmpty()) {
                        if (expr.valueExpr !is FnExpr) {
                            throw Analyser.Error("effects can only be used within a function body: ${expr.name}", expr.loc)
                        }
                        val value = evalEffectfulDef(expr.valueExpr, effects, analyser.slotCount)
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
                        val (value, updatedNsEnv) = evalDefTag(tagExpr, nsEnv, enumName = expr.name.name)
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

                        when (member.kind) {
                            InteropMemberKind.STATIC_FIELD -> {
                                if (!interopLib.isMemberReadable(hostClass, member.memberName)) {
                                    throw Analyser.Error("${member.memberName} is not a readable field on ${member.importAlias} — did you mean ${member.memberName}()?", expr.loc)
                                }
                                val value = interopLib.readMember(hostClass, member.memberName)
                                nsEnv = nsEnv.defInterop(member.qualifiedName, value, member.declaredType)
                            }
                            InteropMemberKind.STATIC_METHOD -> {
                                val rootNode = if (member.memberName == "new")
                                    HostConstructorNode(lang, hostClass)
                                else
                                    HostStaticMethodInvokeNode(lang, hostClass, member.memberName)
                                nsEnv = nsEnv.defInterop(member.qualifiedName, BridjeFunction(rootNode.callTarget), member.declaredType)
                            }
                            InteropMemberKind.INSTANCE_METHOD -> {
                                val rootNode = HostInstanceMethodInvokeNode(lang, member.memberName)
                                nsEnv = nsEnv.defInterop(member.qualifiedName, BridjeFunction(rootNode.callTarget), member.declaredType)
                            }
                            InteropMemberKind.INSTANCE_FIELD -> {
                                val rootNode = HostInstanceFieldReadNode(lang, member.memberName)
                                nsEnv = nsEnv.defInterop(member.qualifiedName, BridjeFunction(rootNode.callTarget), member.declaredType)
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
                    var lastKey: BridjeKey? = null
                    for (name in expr.names) {
                        val key = BridjeKey(name.name)
                        val keyType = FnType(listOf(RecordType.notNull()), freshType()).notNull()
                        val optKeyType = FnType(listOf(RecordType.notNull()), freshType()).notNull()
                        val optName = Symbol.intern("?${name.name}")
                        nsEnv = nsEnv.defKey(name, key, type = keyType)
                        nsEnv = nsEnv.defKey(optName, BridjeOptionalKey(name.name), type = optKeyType)
                        nsEnv = nsEnv.def(optName, BridjeOptionalKey(name.name), type = optKeyType)
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
        if (nsDecl.name == "brj.core") {
            ctx.brjCore = nsEnv
        }

        return nsEnv
    }

    override fun execute(frame: VirtualFrame): Any? {
        val initialNsEnv = when {
            // brj:core starts with Kotlin builtins
            nsDecl?.name == "brj.core" -> NsEnv.withBuiltins(lang).copy(
                requires = resolveRequires(frame),
                imports = nsDecl.imports,
                nsDecl = nsDecl,
                source = source
            )
            // brj:concurrent starts with spawn as a Kotlin builtin
            nsDecl?.name == "brj.concurrent" -> NsEnv.withConcurrentBuiltins(lang).let { base ->
                base.copy(
                    requires = resolveRequires(frame),
                    imports = nsDecl.imports,
                    nsDecl = nsDecl,
                    source = source
                )
            }
            // brj:fs starts with file as a Kotlin builtin and File as the tag
            nsDecl?.name == "brj.fs" -> NsEnv.withFsBuiltins(lang).let { base ->
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
