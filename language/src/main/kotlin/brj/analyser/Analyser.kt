package brj.analyser

import brj.*
import brj.Result
import brj.runtime.BridjeContext
import brj.runtime.BridjeKey
import brj.runtime.BridjeMacro
import brj.runtime.BridjeVector
import brj.runtime.BridjeTagConstructor
import brj.runtime.BridjeTaggedSingleton
import brj.runtime.Loc
import brj.runtime.Symbol
import brj.runtime.sym
import brj.types.*
import brj.types.Nullability.*
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.ExceptionType
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.SourceSection
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_EXPANSION_DEPTH = 100

data class RecurTarget(val arity: Int, val bindings: List<LocalVar>)

data class Analyser(
    private val ctx: BridjeContext,
    private val nsEnv: NsEnv = NsEnv(),
    private val locals: Map<String, LocalVar> = emptyMap(),
    private val capturedVars: Map<String, CapturedVar> = emptyMap(),
    private val nextSlot: AtomicInteger = AtomicInteger(0),
    private val expansionDepth: Int = 0,
    private val errors: MutableList<Error> = mutableListOf(),
    private val gensymScope: MutableMap<String, String> = mutableMapOf(),
    private val recurTarget: RecurTarget? = null,
) {
    val slotCount: Int get() = nextSlot.get()

    @ExportLibrary(InteropLibrary::class)
    data class Error(override val message: String, val loc: SourceSection?) : AbstractTruffleException() {
        @ExportMessage
        fun isException(): Boolean = true

        @ExportMessage
        fun throwException(): RuntimeException = throw this

        @ExportMessage
        fun getExceptionType(): ExceptionType = ExceptionType.PARSE_ERROR

        @ExportMessage
        fun hasExceptionMessage(): Boolean = true

        @ExportMessage
        fun getExceptionMessage(): String = message

        @ExportMessage
        fun hasSourceLocation() = loc != null

        @ExportMessage
        fun getSourceLocation(): SourceSection = loc ?: throw UnsupportedMessageException.create()
    }

    @ExportLibrary(InteropLibrary::class)
    data class Errors(val errors: List<Error>) : AbstractTruffleException() {

        @ExportMessage
        fun isException(): Boolean = true

        @ExportMessage
        fun throwException(): RuntimeException = throw this

        @ExportMessage
        fun getExceptionType(): ExceptionType = ExceptionType.PARSE_ERROR

        @ExportMessage
        fun hasExceptionMessage(): Boolean = true

        @ExportMessage
        fun getExceptionMessage(): String =
            errors.joinToString(prefix = "Errors:\n", separator = "\n") { error ->
                val locStr = error.loc?.let { "(${it.startLine}:${it.startColumn}): " } ?: ""
                " - $locStr${error.message}"
            }

        @ExportMessage
        fun hasArrayElements(): Boolean = true

        @ExportMessage
        fun getArraySize(): Long = errors.size.toLong()

        @ExportMessage
        fun isArrayElementReadable(index: Long): Boolean = index >= 0 && index < errors.size

        @ExportMessage
        fun readArrayElement(index: Long): Error = errors[index.toInt()]
    }

    fun addError(message: String, loc: SourceSection? = null) {
        errors.add(Error(message, loc))
    }

    fun errorExpr(
        message: String,
        loc: SourceSection? = null,
    ): ValueExpr {
        addError(message, loc)
        return ErrorValueExpr(message, loc = loc)
    }

    private fun tryHostLookup(name: String, loc: SourceSection?): TruffleObject? =
        try {
            ctx.truffleEnv.lookupHostSymbol(name) as TruffleObject
        } catch (_: Exception) {
            null
        }

    private sealed interface SymbolResolution {
        data class Captured(val cv: CapturedVar) : SymbolResolution
        data class Local(val lv: LocalVar) : SymbolResolution
        data class Effect(val ns: String?, val gv: GlobalVar) : SymbolResolution
        data class Global(val ns: String?, val gv: GlobalVar) : SymbolResolution
        data class Import(val fqClass: String) : SymbolResolution
        data class Host(val obj: TruffleObject) : SymbolResolution
        object NotFound : SymbolResolution
    }

    private fun resolveSymbol(sym: Symbol, loc: SourceSection?): SymbolResolution {
        val name = sym.name
        capturedVars[name]?.let { return SymbolResolution.Captured(it) }
        locals[name]?.let { return SymbolResolution.Local(it) }
        nsEnv.effectVar(sym)?.let { return SymbolResolution.Effect(nsEnv.nsDecl?.name, it) }
        ctx.brjCore.effectVar(sym)?.let { return SymbolResolution.Effect("brj.core", it) }
        nsEnv[sym]?.let { return SymbolResolution.Global(nsEnv.nsDecl?.name, it) }
        ctx.brjCore[sym]?.let { return SymbolResolution.Global("brj.core", it) }
        nsEnv.imports[name]?.let { return SymbolResolution.Import(it) }
        tryHostLookup(name, loc)?.let { return SymbolResolution.Host(it) }
        return SymbolResolution.NotFound
    }

    private fun analyseSymbol(form: SymbolForm): ValueExpr =
        when (form.sym.name) {
            "nil" -> NilExpr(form.loc)
            "true" -> BoolExpr(true, form.loc)
            "false" -> BoolExpr(false, form.loc)

            "unquote" -> errorExpr("unquote (~) can only be used inside a quote", form.loc)
            "unquote-splicing" -> errorExpr("unquote-splicing (~@) can only be used inside a quote", form.loc)

            else -> when (val res = resolveSymbol(form.sym, form.loc)) {
                is SymbolResolution.Captured -> CapturedVarExpr(res.cv.captureIndex, res.cv.outerLocalVar, form.loc)
                is SymbolResolution.Local -> LocalVarExpr(res.lv, form.loc)
                is SymbolResolution.Effect -> EffectVarExpr(form.sym, res.gv, form.loc)
                is SymbolResolution.Global -> GlobalVarExpr(res.gv, form.loc)
                is SymbolResolution.Import ->
                    tryHostLookup(res.fqClass, form.loc)?.let { HostConstructorExpr(it, form.loc) }
                        ?: errorExpr("Imported class not found: ${res.fqClass}", form.loc)
                is SymbolResolution.Host -> HostConstructorExpr(res.obj, form.loc)
                SymbolResolution.NotFound -> errorExpr("Unknown symbol: ${form.sym.name}", form.loc)
            }
        }

    private fun resolveKey(name: Symbol): GlobalVar? =
        nsEnv.key(name) ?: ctx.brjCore.key(name)

    private fun resolveQualifiedKey(nsAlias: String, member: Symbol): GlobalVar? {
        ctx.namespaces[nsAlias]?.key(member)?.let { return it }
        nsEnv.requires[nsAlias]?.key(member)?.let { return it }
        return null
    }

    private fun analyseKeyword(form: KeywordForm): ValueExpr =
        resolveKey(form.sym)?.let { GlobalVarExpr(it, form.loc) }
            ?: errorExpr("Unknown key: :${form.sym.name}", form.loc)

    private fun analyseQualifiedKeyword(form: QKeywordForm): ValueExpr =
        resolveQualifiedKey(form.ns.name, form.member)?.let { GlobalVarExpr(it, form.loc) }
            ?: errorExpr("Unknown key: $form", form.loc)

    private fun analyseQualifiedDotSymbol(form: QDotSymbolForm): ValueExpr =
        nsEnv.interopVar("${form.ns.name}/${form.member.name}")
            ?.let { GlobalVarExpr(it, form.loc) }
            ?: errorExpr("Unknown host member: $form", form.loc)

    private fun resolveKeyForm(form: Form): GlobalVar? = when (form) {
        is KeywordForm -> resolveKey(form.sym)
        is QKeywordForm -> resolveQualifiedKey(form.ns.name, form.member)
        else -> null
    }

    private fun analyseRecord(form: RecordForm): ValueExpr {
        val els = form.els

        if (els.size % 2 != 0) return errorExpr("record literal must have even number of forms", form.loc)

        val fields = mutableListOf<Pair<String, ValueExpr>>()

        for (i in els.indices step 2) {
            val keyForm = els[i]
            if (keyForm !is KeywordForm && keyForm !is QKeywordForm)
                return errorExpr("record keys must be keywords", keyForm.loc)
            val keyValue = resolveKeyForm(keyForm)?.value
            if (keyValue !is BridjeKey) {
                return errorExpr("$keyForm is not a key", keyForm.loc)
            }
            val valueExpr = analyseValueExpr(els[i + 1])
            fields.add(keyValue.name to valueExpr)
        }

        return RecordExpr(fields, form.loc)
    }

    private fun analyseQualifiedSymbol(form: QSymbolForm): ValueExpr {
        // Try Bridje namespace first (fully qualified)
        ctx.namespaces[form.ns.name]?.let { ns ->
            ns.effectVar(form.member)?.let { return EffectVarExpr(form.member, it, form.loc) }
            val globalVar = ns[form.member]
                ?: return errorExpr("Unknown symbol: ${form.member.name} in namespace ${form.ns.name}", form.loc)
            return GlobalVarExpr(globalVar, form.loc)
        }

        // Check if namespace is a require alias
        nsEnv.requires[form.ns.name]?.let { ns ->
            ns.effectVar(form.member)?.let { return EffectVarExpr(form.member, it, form.loc) }
            val globalVar = ns[form.member]
                ?: return errorExpr("Unknown symbol: ${form.member.name} in required namespace ${form.ns.name}", form.loc)
            return GlobalVarExpr(globalVar, form.loc)
        }

        // Check for typed interop declarations
        nsEnv.interopVar("${form.ns.name}/${form.member.name}")?.let { return GlobalVarExpr(it, form.loc) }

        val fqClass = nsEnv.imports[form.ns.name] ?: form.ns.name

        val hostClass =
            try {
                ctx.truffleEnv.lookupHostSymbol(fqClass) as TruffleObject
            } catch (_: Exception) {
                // Namespace lookup failed - try the full qualified name as a Java class
                // (e.g., java.lang.String where namespace=java.lang, member=String)
                val fullName = "${form.ns.name}.${form.member.name}"
                tryHostLookup(fullName, form.loc)?.let { return HostConstructorExpr(it, form.loc) }
                return errorExpr("Unknown namespace: ${form.ns.name}", form.loc)
            }

        return HostStaticMethodExpr(hostClass, form.member.name, form.loc)
    }

    private fun analyseListValueExpr(form: ListForm): ValueExpr {
        val els = form.els
        val first = els.firstOrNull() ?: TODO("empty list not supported yet")

        return when (first) {
            is SymbolForm -> when (first.sym.name) {
                "let" -> analyseLet(form)
                "fn" -> analyseFn(form)
                "do" -> analyseDo(form)
                "if" -> analyseIf(form)
                "case" -> analyseCase(form)
                "try" -> analyseTry(form)
                "quote" -> analyseQuote(form)
                "squote" -> analyseSquote(form)
                "unquote" -> errorExpr("unquote (~) can only be used inside a quote", form.loc)
                "unquote-splicing" -> errorExpr("unquote-splicing (~@) can only be used inside a quote", form.loc)
                "set!" -> analyseSet(form)
                "withFx" -> analyseWithFx(form)
                "with" -> analyseWith(form)
                "loop" -> analyseLoop(form)
                "recur" -> analyseRecur(form)
                "def" -> errorExpr("def not allowed in value position", form.loc)
                "decl" -> errorExpr("decl not allowed in value position", form.loc)
                "tag" -> errorExpr("tag not allowed in value position", form.loc)
                "enum" -> errorExpr("enum not allowed in value position", form.loc)
                "defmacro" -> errorExpr("defmacro not allowed in value position", form.loc)
                "defkeys" -> errorExpr("defkeys has been replaced: use decl: :name Str", form.loc)
                "defx" -> errorExpr("defx not allowed in value position", form.loc)
                "lang" -> analyseLang(form)
                else -> analyseCall(form)
            }
            else -> analyseCall(form)
        }
    }

    private fun analyseDo(form: ListForm): ValueExpr {
        val bodyForms = form.els.drop(1)
        if (bodyForms.isEmpty()) return errorExpr("do requires at least one expression", form.loc)
        return analyseBody(bodyForms, form.loc)
    }

    private fun callFormConstructor(name: String, args: List<ValueExpr>, loc: SourceSection?): ValueExpr {
        val sym = Symbol.intern(name)
        // Quoting machinery: form constructors and Symbol/Var must be reachable regardless of the user's ns/requires.
        val constructor = ctx.namespaces["brj.rdr"]?.get(sym) ?: ctx.brjCore[sym]
            ?: return errorExpr("$name constructor not found", loc)
        return CallExpr(GlobalVarExpr(constructor, loc), args, loc)
    }

    private fun isUnquote(form: Form): Form? =
        (form as? ListForm)
            ?.takeIf { (it.els.firstOrNull() as? SymbolForm)?.sym?.name == "unquote" && it.els.size == 2 }
            ?.els?.get(1)

    private fun isUnquoteSplicing(form: Form): Form? =
        (form as? ListForm)
            ?.takeIf { (it.els.firstOrNull() as? SymbolForm)?.sym?.name == "unquote-splicing" && it.els.size == 2 }
            ?.els?.get(1)

    private fun collFormConstructor(
        name: String,
        els: List<Form>,
        loc: SourceSection?,
        resolveSymbols: Boolean
    ): ValueExpr {
        val hasSplice = els.any { isUnquoteSplicing(it) != null }

        if (!hasSplice) {
            val elements = els.map { analyseQuotedForm(it, resolveSymbols) }
            return callFormConstructor(name, listOf(VectorExpr(elements, loc)), loc)
        }

        val chunks = mutableListOf<ValueExpr>()
        val currentGroup = mutableListOf<ValueExpr>()

        fun flushGroup() {
            if (currentGroup.isNotEmpty()) {
                chunks.add(VectorExpr(currentGroup.toList(), loc))
                currentGroup.clear()
            }
        }

        for (el in els) {
            val spliceInner = isUnquoteSplicing(el)
            if (spliceInner != null) {
                flushGroup()
                chunks.add(analyseValueExpr(spliceInner))
            } else {
                currentGroup.add(analyseQuotedForm(el, resolveSymbols))
            }
        }
        flushGroup()

        val concatSym = "concat".sym
        val concatVar = nsEnv[concatSym] ?: ctx.brjCore[concatSym]
            ?: return errorExpr("concat not found", loc)

        val concatenated = chunks.fold(VectorExpr(emptyList(), loc) as ValueExpr) { acc, chunk ->
            CallExpr(GlobalVarExpr(concatVar, loc), listOf(acc, chunk), loc)
        }

        return callFormConstructor(name, listOf(concatenated), loc)
    }

    private fun withLocMeta(expr: ValueExpr, loc: SourceSection?): ValueExpr {
        if (loc == null) return expr
        val withMetaVar = ctx.brjCore["with-meta".sym] ?: return expr
        val meta = RecordExpr(listOf("loc" to TruffleObjectExpr(Loc(loc), loc)), loc)
        return CallExpr(GlobalVarExpr(withMetaVar, loc), listOf(expr, meta), loc)
    }

    private fun analyseQuotedForm(form: Form, resolveSymbols: Boolean): ValueExpr {
        isUnquote(form)?.let { return analyseValueExpr(it) }
        return withLocMeta(analyseQuotedFormInner(form, resolveSymbols), form.loc)
    }

    private fun gensymOrName(name: String): String =
        if (name.endsWith("#")) {
            val baseName = name.dropLast(1)
            gensymScope.getOrPut(baseName) { "${baseName}__${ctx.nextGensymId()}" }
        } else name

    // Names that, as the head of a list, are analyser-level syntax rather than var references.
    // Symbols that resolve to these stay bare inside a squote walk; the expanded form's analyser
    // will pick them up in its own dispatch table.
    private val specialFormNames = setOf(
        "if", "let", "fn", "case", "try", "catch", "finally", "do", "recur", "loop",
        "quote", "squote", "unquote", "unquote-splicing",
        "withFx", "with", "lang", "set!",
        "ns", "require", "import",
        "def", "decl", "defx", "defmacro", "defkeys", "tag", "enum",
        "nil", "true", "false",
        "&"
    )

    private fun resolveSquoteSymbol(sym: Symbol, loc: SourceSection?): ValueExpr {
        // Caller has already confirmed sym isn't a special form or gensym.
        val (ns, member) = when (val res = resolveSymbol(sym, loc)) {
            is SymbolResolution.Effect -> (res.ns ?: return errorExpr("Cannot squote: ${sym.name} has no namespace", loc)) to sym.name
            is SymbolResolution.Global -> (res.ns ?: return errorExpr("Cannot squote: ${sym.name} has no namespace", loc)) to sym.name
            is SymbolResolution.Captured, is SymbolResolution.Local ->
                return errorExpr("Cannot resolve local in squote: ${sym.name} (did you mean ~${sym.name}?)", loc)
            is SymbolResolution.Import, is SymbolResolution.Host ->
                return errorExpr("Cannot squote host class: ${sym.name}", loc)
            SymbolResolution.NotFound ->
                return errorExpr("Unknown symbol: ${sym.name}", loc)
        }
        return callFormConstructor(
            "QSymbolForm",
            listOf(
                callFormConstructor("Symbol", listOf(StringExpr(ns, loc)), loc),
                callFormConstructor("Symbol", listOf(StringExpr(member, loc)), loc)
            ),
            loc
        )
    }

    private fun analyseQuotedFormInner(form: Form, resolveSymbols: Boolean): ValueExpr =
        when (form) {
            is IntForm ->
                callFormConstructor("Int", listOf(IntExpr(form.value, form.loc)), form.loc)

            is DoubleForm ->
                callFormConstructor("Double", listOf(DoubleExpr(form.value, form.loc)), form.loc)

            is BigIntForm ->
                callFormConstructor("BigInt", listOf(BigIntExpr(form.value, form.loc)), form.loc)

            is BigDecForm ->
                callFormConstructor("BigDec", listOf(BigDecExpr(form.value, form.loc)), form.loc)

            is StringForm ->
                callFormConstructor("String", listOf(StringExpr(form.value, form.loc)), form.loc)

            is SymbolForm -> {
                val name = gensymOrName(form.sym.name)
                val isGensymmed = name != form.sym.name
                if (resolveSymbols && !isGensymmed && name !in specialFormNames) {
                    resolveSquoteSymbol(Symbol.intern(name), form.loc)
                } else {
                    callFormConstructor(
                        "SymbolForm",
                        listOf(callFormConstructor("Symbol", listOf(StringExpr(name, form.loc)), form.loc)),
                        form.loc
                    )
                }
            }

            is KeywordForm ->
                callFormConstructor(
                    "KeywordForm",
                    listOf(callFormConstructor("Symbol", listOf(StringExpr(form.sym.name, form.loc)), form.loc)),
                    form.loc
                )

            is QKeywordForm ->
                callFormConstructor(
                    "QKeywordForm",
                    listOf(
                        callFormConstructor("Symbol", listOf(StringExpr(form.ns.name, form.loc)), form.loc),
                        callFormConstructor("Symbol", listOf(StringExpr(form.member.name, form.loc)), form.loc)
                    ),
                    form.loc
                )

            is DotSymbolForm ->
                callFormConstructor(
                    "DotSymbolForm",
                    listOf(callFormConstructor("Symbol", listOf(StringExpr(form.sym.name, form.loc)), form.loc)),
                    form.loc
                )

            is QDotSymbolForm ->
                callFormConstructor(
                    "QDotSymbolForm",
                    listOf(
                        callFormConstructor("Symbol", listOf(StringExpr(form.ns.name, form.loc)), form.loc),
                        callFormConstructor("Symbol", listOf(StringExpr(form.member.name, form.loc)), form.loc)
                    ),
                    form.loc
                )

            is QSymbolForm -> {
                val member = gensymOrName(form.member.name)
                val nsName = if (resolveSymbols && member == form.member.name) {
                    // Follow require aliases to the real ns name. Error if unknown.
                    ctx.namespaces[form.ns.name]?.takeIf { it[form.member] != null }?.let { form.ns.name }
                        ?: nsEnv.requires[form.ns.name]?.let { req ->
                            if (req[form.member] != null) req.nsDecl?.name else null
                        }
                        ?: return errorExpr("Unknown symbol: ${form.ns.name}/${form.member.name}", form.loc)
                } else form.ns.name
                callFormConstructor(
                    "QSymbolForm",
                    listOf(
                        callFormConstructor("Symbol", listOf(StringExpr(nsName, form.loc)), form.loc),
                        callFormConstructor("Symbol", listOf(StringExpr(member, form.loc)), form.loc)
                    ),
                    form.loc
                )
            }

            is ListForm -> collFormConstructor("List", form.els, form.loc, resolveSymbols)
            is VectorForm -> collFormConstructor("Vector", form.els, form.loc, resolveSymbols)
            is SetForm -> collFormConstructor("Set", form.els, form.loc, resolveSymbols)
            is RecordForm -> collFormConstructor("Record", form.els, form.loc, resolveSymbols)
        }

    private fun analyseQuote(form: ListForm): ValueExpr {
        if (form.els.size != 2) return errorExpr("quote requires exactly one argument", form.loc)
        return analyseQuotedForm(form.els[1], resolveSymbols = false)
    }

    private fun analyseSquote(form: ListForm): ValueExpr {
        if (form.els.size != 2) return errorExpr("squote requires exactly one argument", form.loc)
        return analyseQuotedForm(form.els[1], resolveSymbols = true)
    }

    private fun analyseIf(form: ListForm): ValueExpr {
        val els = form.els
        if (els.size != 4) return errorExpr("if requires exactly 3 arguments: predicate, then, else", form.loc)

        val nonTail = copy(recurTarget = null)
        return IfExpr(
            nonTail.analyseValueExpr(els[1]),
            analyseValueExpr(els[2]),
            analyseValueExpr(els[3]),
            form.loc
        )
    }

    internal fun withLocal(name: String): Pair<Analyser, LocalVar> {
        val lv = LocalVar(name, nextSlot.getAndIncrement())
        return Pair(copy(locals = locals + (name to lv)), lv)
    }

    private fun analyseCase(form: ListForm): ValueExpr {
        val els = form.els
        if (els.size < 3) return errorExpr("case requires a scrutinee and at least one branch", form.loc)

        val scrutinee = copy(recurTarget = null).analyseValueExpr(els[1])
        val branchForms = els.drop(2)

        val branches = mutableListOf<CaseBranch>()
        var i = 0
        while (i < branchForms.size) {
            val patternForm = branchForms[i]

            val isPattern = when (patternForm) {
                is SymbolForm -> patternForm.sym.name == "nil" || i + 1 < branchForms.size
                is QSymbolForm -> i + 1 < branchForms.size
                is ListForm -> {
                    val first = patternForm.els.firstOrNull()
                    first is SymbolForm || first is QSymbolForm
                }
                else -> false
            }

            if (isPattern) {
                val bodyForm = branchForms.getOrNull(i + 1)
                if (bodyForm == null) {
                    errorExpr("case branch missing body expression", patternForm.loc)
                    i += 1
                    continue
                }
                when (val branchResult = analyseCaseBranch(patternForm, bodyForm)) {
                    is Result.Ok -> branches.add(branchResult.value)
                    is Result.Err -> errors.add(branchResult.error)
                }
                i += 2
            } else {
                // Last form is not a pattern - treat as default
                if (i != branchForms.size - 1) {
                    errorExpr("default expression must be last in case", patternForm.loc)
                }
                val bodyExpr = analyseValueExpr(patternForm)
                branches.add(CaseBranch(DefaultPattern(patternForm.loc), bodyExpr, patternForm.loc))
                i += 1
            }
        }

        if (branches.isEmpty()) return errorExpr("case requires at least one branch", form.loc)

        // Exhaustiveness check for enum case expressions
        val hasDefault = branches.any { it.pattern is DefaultPattern || it.pattern is CatchAllBindingPattern }
        if (!hasDefault) {
            val tagNames = branches.mapNotNull { branch ->
                val pattern = branch.pattern
                if (pattern is TagPattern) {
                    when (val tv = pattern.tagValue) {
                        is BridjeTagConstructor -> tv.tag
                        is BridjeTaggedSingleton -> tv.tag
                        else -> null
                    }
                } else null
            }

            if (tagNames.isNotEmpty()) {
                val firstTagSym = Symbol.intern(tagNames.first())
                val enumName = nsEnv.enumForTag(firstTagSym)
                    ?: ctx.brjCore.enumForTag(firstTagSym)
                    ?: nsEnv.requires.values.firstNotNullOfOrNull { it.enumForTag(firstTagSym) }

                if (enumName != null) {
                    val allVariants = nsEnv.enums[enumName]
                        ?: ctx.brjCore.enums[enumName]
                        ?: nsEnv.requires.values.firstNotNullOfOrNull { it.enums[enumName] }
                        ?: emptySet()
                    val coveredSyms = tagNames.map { Symbol.intern(it) }.toSet()
                    if (coveredSyms.all { it in allVariants }) {
                        val missing = allVariants - coveredSyms
                        if (missing.isNotEmpty()) {
                            addError("Non-exhaustive case: missing variants ${missing.joinToString(", ")} of enum ${enumName.name}", form.loc)
                        }
                    }
                }
            }
        }

        return CaseExpr(scrutinee, branches, form.loc)
    }

    private fun resolveTag(name: String, loc: SourceSection?): Result<Error, Any> {
        val sym = Symbol.intern(name)
        val globalVar = nsEnv[sym] ?: ctx.brjCore[sym]
            ?: return Result.Err(Error("Unknown tag: $name", loc))
        val value = globalVar.value
            ?: return Result.Err(Error("Tag $name has no value", loc))
        return Result.Ok(value)
    }

    private fun resolveQualifiedTag(nsAlias: String, member: Symbol, loc: SourceSection?): Result<Error, Any> {
        val ns = ctx.namespaces[nsAlias] ?: nsEnv.requires[nsAlias]
            ?: return Result.Err(Error("Unknown namespace: $nsAlias", loc))
        val globalVar = ns[member]
            ?: return Result.Err(Error("Unknown tag: $nsAlias/${member.name}", loc))
        val value = globalVar.value
            ?: return Result.Err(Error("Tag $nsAlias/${member.name} has no value", loc))
        return Result.Ok(value)
    }

    private fun analyseBindingNames(els: List<Form>): Result<Error, List<String>> {
        val names = mutableListOf<String>()
        for (el in els) {
            val sym = el as? SymbolForm
                ?: return Result.Err(Error("case pattern bindings must be symbols", el.loc))
            names.add(sym.sym.name)
        }
        return Result.Ok(names)
    }

    private fun analyseCaseBranch(patternForm: Form, bodyForm: Form): Result<Error, CaseBranch> =
        when (patternForm) {
            is SymbolForm -> {
                val name = patternForm.sym.name
                when {
                    name == "nil" -> {
                        val bodyExpr = analyseValueExpr(bodyForm)
                        Result.Ok(CaseBranch(NilPattern(patternForm.loc), bodyExpr, patternForm.loc))
                    }
                    name[0].isUpperCase() -> {
                        resolveTag(name, patternForm.loc).map { tagValue ->
                            val bodyExpr = analyseValueExpr(bodyForm)
                            CaseBranch(TagPattern(tagValue, emptyList(), patternForm.loc), bodyExpr, patternForm.loc)
                        }
                    }
                    else -> {
                        // catchall binding pattern: lowercase symbol binds scrutinee
                        val (newAnalyser, localVar) = withLocal(name)
                        val bodyExpr = newAnalyser.analyseValueExpr(bodyForm)
                        Result.Ok(CaseBranch(CatchAllBindingPattern(localVar, patternForm.loc), bodyExpr, patternForm.loc))
                    }
                }
            }

            is QSymbolForm -> {
                resolveQualifiedTag(patternForm.ns.name, patternForm.member, patternForm.loc).map { tagValue ->
                    val bodyExpr = analyseValueExpr(bodyForm)
                    CaseBranch(TagPattern(tagValue, emptyList(), patternForm.loc), bodyExpr, patternForm.loc)
                }
            }

            is ListForm -> {
                val tagResult: Result<Error, Pair<Any, SourceSection?>> = when (val head = patternForm.els.firstOrNull()) {
                    is SymbolForm -> {
                        val tagName = head.sym.name
                        if (!tagName[0].isUpperCase()) {
                            Result.Err(Error("case pattern tag must be capitalized: $tagName", head.loc))
                        } else {
                            resolveTag(tagName, head.loc).map { it to head.loc }
                        }
                    }
                    is QSymbolForm -> resolveQualifiedTag(head.ns.name, head.member, head.loc).map { it to head.loc }
                    null -> Result.Err(Error("case pattern must start with a tag name", patternForm.loc))
                    else -> Result.Err(Error("case pattern must start with a tag name", patternForm.loc))
                }

                tagResult.flatMap { (tagValue, tagLoc) ->
                    analyseBindingNames(patternForm.els.drop(1)).map { bindingNames ->
                        var branchAnalyser = this
                        val bindings = mutableListOf<LocalVar>()

                        for (bindingName in bindingNames) {
                            val (newAnalyser, localVar) = branchAnalyser.withLocal(bindingName)
                            branchAnalyser = newAnalyser
                            bindings.add(localVar)
                        }

                        val bodyExpr = branchAnalyser.analyseValueExpr(bodyForm)
                        CaseBranch(TagPattern(tagValue, bindings, patternForm.loc), bodyExpr, patternForm.loc)
                    }
                }
            }
            else -> Result.Err(Error("case pattern must be a tag", patternForm.loc))
        }

    private fun analyseTry(form: ListForm): ValueExpr {
        val els = form.els
        if (els.size < 3) return errorExpr("try requires a body and at least a catch clause", form.loc)

        // Find catch and finally clauses
        var catchForm: ListForm? = null
        var finallyForm: ListForm? = null
        val bodyForms = mutableListOf<Form>()

        for (el in els.drop(1)) {
            if (el is ListForm && el.els.firstOrNull().let { it is SymbolForm && it.sym.name == "catch" }) {
                catchForm = el
            } else if (el is ListForm && el.els.firstOrNull().let { it is SymbolForm && it.sym.name == "finally" }) {
                finallyForm = el
            } else {
                bodyForms.add(el)
            }
        }

        if (catchForm == null) return errorExpr("try requires a catch clause", form.loc)

        val bodyExpr = analyseBody(bodyForms, form.loc)

        // Parse catch branches — same as case branches, pattern-matching on the anomaly value
        val catchEls = catchForm.els.drop(1)
        val catchBranches = mutableListOf<CaseBranch>()
        var i = 0
        while (i < catchEls.size) {
            val patternForm = catchEls[i]

            val isPattern = when (patternForm) {
                is SymbolForm -> patternForm.sym.name[0].isUpperCase() || patternForm.sym.name == "nil" ||
                    (patternForm.sym.name[0].isLowerCase() && i + 1 < catchEls.size)
                is ListForm -> {
                    val first = patternForm.els.firstOrNull()
                    first is SymbolForm && first.sym.name[0].isUpperCase()
                }
                else -> false
            }

            if (isPattern) {
                val bodyForm = catchEls.getOrNull(i + 1)
                if (bodyForm == null) {
                    errorExpr("catch branch missing body expression", patternForm.loc)
                    i += 1
                    continue
                }
                when (val branchResult = analyseCaseBranch(patternForm, bodyForm)) {
                    is Result.Ok -> catchBranches.add(branchResult.value)
                    is Result.Err -> errors.add(branchResult.error)
                }
                i += 2
            } else {
                if (i != catchEls.size - 1) {
                    errorExpr("default expression must be last in catch", patternForm.loc)
                }
                val catchBodyExpr = analyseValueExpr(patternForm)
                catchBranches.add(CaseBranch(DefaultPattern(patternForm.loc), catchBodyExpr, patternForm.loc))
                i += 1
            }
        }

        if (catchBranches.isEmpty()) return errorExpr("catch requires at least one branch", catchForm.loc)

        val finallyExpr = if (finallyForm != null) {
            val finallyBodyForms = finallyForm.els.drop(1)
            if (finallyBodyForms.isEmpty()) {
                errorExpr("finally requires a body", finallyForm.loc)
            } else {
                analyseBody(finallyBodyForms, finallyForm.loc)
            }
        } else null

        return TryCatchExpr(bodyExpr, catchBranches, finallyExpr, form.loc)
    }

    private fun analyseBody(forms: List<Form>, loc: SourceSection?): ValueExpr =
        when {
            forms.isEmpty() -> errorExpr("body requires at least one expression", loc)
            forms.size == 1 -> analyseValueExpr(forms[0])
            else -> {
                val nonTail = copy(recurTarget = null)
                val sideEffects = forms.dropLast(1).map { nonTail.analyseValueExpr(it) }
                val result = analyseValueExpr(forms.last())
                DoExpr(sideEffects, result, loc)
            }
        }

    private fun analyseBindings(
        bindingEls: List<Form>,
        bodyForms: List<Form>,
        loc: SourceSection?
    ): ValueExpr {
        if (bindingEls.isEmpty()) return analyseBody(bodyForms, loc)

        val nameForm = bindingEls[0] as? SymbolForm
            ?: return errorExpr("binding name must be a symbol", bindingEls[0].loc)

        val valueForm = bindingEls[1]

        val bindingExpr = copy(recurTarget = null).analyseValueExpr(valueForm)
        val (newAnalyser, localVar) = withLocal(nameForm.sym.name)

        val bodyExpr = newAnalyser.analyseBindings(bindingEls.drop(2), bodyForms, loc)

        return LetExpr(localVar, bindingExpr, bodyExpr, loc)
    }

    private fun analyseSet(form: ListForm): ValueExpr {
        val els = form.els
        if (els.size != 4) return errorExpr("set! requires exactly 3 arguments: record, key, value", form.loc)

        val keyForm = els[2]
        if (keyForm !is KeywordForm && keyForm !is QKeywordForm)
            return errorExpr("set! second argument must be a keyword", keyForm.loc)

        val keyVar = resolveKeyForm(keyForm)
            ?: return errorExpr("Unknown key: $keyForm", keyForm.loc)
        val keyValue = keyVar.value
        if (keyValue !is BridjeKey) return errorExpr("$keyForm is not a key", keyForm.loc)

        val recordExpr = analyseValueExpr(els[1])
        val valueExpr = analyseValueExpr(els[3])

        return RecordSetExpr(recordExpr, keyValue.name, valueExpr, form.loc)
    }

    private fun resolveDotSymbolKey(form: DotSymbolForm): GlobalVar? =
        nsEnv.key(form.sym) ?: ctx.brjCore.key(form.sym)

    private fun resolveQualifiedDotSymbolKey(form: QDotSymbolForm): GlobalVar? {
        ctx.namespaces[form.ns.name]?.key(form.member)?.let { return it }
        nsEnv.requires[form.ns.name]?.key(form.member)?.let { return it }
        return null
    }

    private fun analyseWith(form: ListForm): ValueExpr {
        val els = form.els
        if (els.size < 2)
            return errorExpr("with requires a record and at least one field/value pair", form.loc)

        val updates = els.drop(2)
        if (updates.size % 2 != 0)
            return errorExpr("with requires an even number of field/value forms after the record", form.loc)

        if (updates.isEmpty())
            return errorExpr("with requires a record and at least one field/value pair", form.loc)

        val recordExpr = analyseValueExpr(els[1])

        val fields = mutableListOf<Pair<String, ValueExpr>>()
        for (i in updates.indices step 2) {
            val fieldForm = updates[i]
            val keyVar = when (fieldForm) {
                is DotSymbolForm ->
                    resolveDotSymbolKey(fieldForm)
                        ?: return errorExpr("Unknown field: .${fieldForm.sym.name}", fieldForm.loc)
                is QDotSymbolForm ->
                    resolveQualifiedDotSymbolKey(fieldForm)
                        ?: return errorExpr("Unknown field: $fieldForm", fieldForm.loc)
                else ->
                    return errorExpr("with field selector must be a dot-symbol (.field)", fieldForm.loc)
            }
            val keyValue = keyVar.value
            if (keyValue !is BridjeKey) return errorExpr("$fieldForm is not a key", fieldForm.loc)
            val valueExpr = analyseValueExpr(updates[i + 1])
            fields.add(keyValue.name to valueExpr)
        }

        return RecordUpdateExpr(recordExpr, fields, form.loc)
    }

    private fun analyseLet(form: ListForm): ValueExpr {
        val els = form.els
        val bindingsForm = els.getOrNull(1) as? VectorForm
            ?: return errorExpr("let requires a vector of bindings", form.loc)

        val bindingEls = bindingsForm.els
        if (bindingEls.size % 2 != 0) {
            return errorExpr("let bindings must have even number of forms", form.loc)
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) {
            return errorExpr("let requires a body", form.loc)
        }

        return analyseBindings(bindingEls, bodyForms, form.loc)
    }

    private fun analyseLoop(form: ListForm): ValueExpr {
        val els = form.els
        val bindingsForm = els.getOrNull(1) as? VectorForm
            ?: return errorExpr("loop requires a vector of bindings", form.loc)

        val bindingEls = bindingsForm.els
        if (bindingEls.size % 2 != 0) {
            return errorExpr("loop bindings must have even number of forms", form.loc)
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) {
            return errorExpr("loop requires a body", form.loc)
        }

        val nonTail = copy(recurTarget = null)
        val bindings = mutableListOf<Pair<LocalVar, ValueExpr>>()
        var analyser = this
        for (i in bindingEls.indices step 2) {
            val nameForm = bindingEls[i] as? SymbolForm
                ?: return errorExpr("loop binding name must be a symbol", bindingEls[i].loc)
            val bindingExpr = nonTail.analyseValueExpr(bindingEls[i + 1])
            val (newAnalyser, localVar) = analyser.withLocal(nameForm.sym.name)
            analyser = newAnalyser
            bindings.add(localVar to bindingExpr)
        }

        val loopAnalyser = analyser.copy(recurTarget = RecurTarget(bindings.size, bindings.map { it.first }))
        val bodyExpr = loopAnalyser.analyseBody(bodyForms, form.loc)

        return LoopExpr(bindings, bodyExpr, form.loc)
    }

    private fun analyseRecur(form: ListForm): ValueExpr {
        val target = recurTarget
            ?: return errorExpr("recur outside of loop or fn", form.loc)

        val argForms = form.els.drop(1)
        if (argForms.size != target.arity) {
            return errorExpr("recur expects ${target.arity} arguments, got ${argForms.size}", form.loc)
        }

        val nonTail = copy(recurTarget = null)
        val argExprs = argForms.map { nonTail.analyseValueExpr(it) }
        return RecurExpr(target.bindings, argExprs, form.loc)
    }

    private fun analyseFn(form: ListForm): ValueExpr {
        val els = form.els
        val sigForm = els.getOrNull(1) as? ListForm
            ?: return errorExpr("fn requires a signature list (fn-name & params)", form.loc)

        val sigEls = sigForm.els
        val fnName = (sigEls.firstOrNull() as? SymbolForm)?.sym?.name
            ?: return errorExpr("fn signature must start with a name", sigForm.loc)

        val params = mutableListOf<String>()
        for (el in sigEls.drop(1)) {
            val sym = el as? SymbolForm
                ?: return errorExpr("fn parameter must be a symbol", el.loc)
            params.add(sym.sym.name)
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) return errorExpr("fn requires a body", form.loc)

        val paramSet = params.toSet()
        val captures = mutableListOf<CapturedVar>()
        val innerCapturedVars = mutableMapOf<String, CapturedVar>()
        var nextCaptureIndex = 0

        for ((name, outerLv) in locals) {
            if (name !in paramSet) {
                val cv = CapturedVar(name, outerLv, nextCaptureIndex++, FrameSlotCapture(outerLv.slot))
                captures.add(cv)
                innerCapturedVars[name] = cv
            }
        }

        for ((name, outerCv) in capturedVars) {
            if (name !in paramSet && name !in innerCapturedVars) {
                val cv = CapturedVar(name, outerCv.outerLocalVar, nextCaptureIndex++, TransitiveCapture(outerCv.captureIndex))
                captures.add(cv)
                innerCapturedVars[name] = cv
            }
        }

        var fnAnalyser = Analyser(ctx, nsEnv, capturedVars = innerCapturedVars.toMap(), errors = errors, gensymScope = gensymScope)
        val paramLvs = mutableListOf<LocalVar>()
        for (param in params) {
            val (newAnalyser, lv) = fnAnalyser.withLocal(param)
            fnAnalyser = newAnalyser
            paramLvs.add(lv)
        }

        fnAnalyser = fnAnalyser.copy(recurTarget = RecurTarget(paramLvs.size, paramLvs))
        val bodyExpr = fnAnalyser.analyseBody(bodyForms, form.loc)

        return FnExpr(fnName, paramLvs, bodyExpr, fnAnalyser.slotCount, captures, isVariadic = false, loc = form.loc)
    }

    private fun analyseCall(form: ListForm): ValueExpr {
        val els = form.els
        val fnExpr = analyseValueExpr(els[0])

        // Check for macro expansion
        if (fnExpr is GlobalVarExpr) {
            val value = fnExpr.globalVar.value
            if (value is BridjeMacro) {
                if (expansionDepth >= MAX_EXPANSION_DEPTH) {
                    return errorExpr("Maximum macro expansion depth ($MAX_EXPANSION_DEPTH) exceeded", form.loc)
                }
                val interop = InteropLibrary.getUncached()
                val argForms = els.drop(1)
                val args: Array<Any> = if (value.isVariadic) {
                    val fixed = argForms.take(value.fixedArity)
                    val rest = BridjeVector(argForms.drop(value.fixedArity))
                    (fixed + rest).toTypedArray()
                } else {
                    argForms.toTypedArray<Any>()
                }
                val expanded = interop.execute(value.fn, *args) as Form
                return copy(expansionDepth = expansionDepth + 1)
                    .analyseValueExpr(expanded)
            }
        }

        val argExprs = els.drop(1).map { analyseValueExpr(it) }
        return CallExpr(fnExpr, argExprs, form.loc)
    }

    private fun analyseValueExprInner(form: Form): ValueExpr {
        return when (form) {
            is IntForm -> IntExpr(form.value, form.loc)
            is DoubleForm -> DoubleExpr(form.value, form.loc)
            is BigIntForm -> BigIntExpr(form.value, form.loc)
            is BigDecForm -> BigDecExpr(form.value, form.loc)
            is StringForm -> StringExpr(form.value, form.loc)
            is SymbolForm -> analyseSymbol(form)
            is KeywordForm -> analyseKeyword(form)
            is QKeywordForm -> analyseQualifiedKeyword(form)
            is QSymbolForm -> analyseQualifiedSymbol(form)
            is DotSymbolForm -> errorExpr("bare instance-member references are not supported yet: $form", form.loc)
            is QDotSymbolForm -> analyseQualifiedDotSymbol(form)
            is ListForm -> analyseListValueExpr(form)
            is VectorForm -> VectorExpr(form.els.map { analyseValueExpr(it) }, form.loc)
            is SetForm -> SetExpr(form.els.map { analyseValueExpr(it) }, form.loc)
            is RecordForm -> analyseRecord(form)
        }
    }

    fun analyseValueExpr(form: Form): ValueExpr {
        val inner = analyseValueExprInner(form)
        val meta = form.staticMeta ?: return inner

        val withMetaVar = ctx.brjCore["with-meta".sym]
            ?: return errorExpr("with-meta not found in brj.core", form.loc)

        return CallExpr(
            GlobalVarExpr(withMetaVar, form.loc),
            listOf(inner, analyseValueExpr(meta)),
            form.loc
        )
    }

    private fun errorType(message: String, loc: SourceSection?): Type {
        addError(message, loc)
        return errorType()
    }

    private fun analyseTypeSymbol(name: String, loc: SourceSection?, typeVars: Map<String, TypeVar>): Type =
        when (name) {
            "Int" -> IntType.notNull()
            "Str" -> StringType.notNull()
            "Bool" -> BoolType.notNull()
            "Double" -> FloatType.notNull()
            "BigInt" -> BigIntType.notNull()
            "BigDec" -> BigDecType.notNull()
            "Bytes" -> BytesType.notNull()
            "Form" -> FormType.notNull()
            "Nothing" -> nullType()
            else -> when {
                name[0].isUpperCase() -> {
                    // Check if it's an enum type first
                    val sym = Symbol.intern(name)
                    val enumVariants = nsEnv.enums[sym] ?: ctx.brjCore.enums[sym]
                    if (enumVariants != null) {
                        EnumType(name).notNull()
                    } else {
                        val ns = nsEnv[sym]?.let { nsEnv.nsDecl?.name ?: "" }
                            ?: ctx.brjCore[sym]?.let { "brj.core" }
                        if (ns != null) {
                            TagType(ns, name).notNull()
                        } else {
                            val importFqClass = nsEnv.imports[name]
                            if (importFqClass != null) {
                                HostType(importFqClass).notNull()
                            } else {
                                errorType("Unknown type: $name", loc)
                            }
                        }
                    }
                }
                name in typeVars -> Type(NOT_NULL, typeVars[name]!!, null)
                else -> errorType("Unsupported type form: $name", loc)
            }
        }

    internal fun analyseTypeForm(form: Form, typeVars: Map<String, TypeVar>): Type =
        when (form) {
            is SymbolForm -> {
                val name = form.sym.name
                if (name.endsWith("?")) {
                    val inner = analyseTypeSymbol(name.dropLast(1), form.loc, typeVars)
                    Type(NULLABLE, inner.tv, inner.base)
                } else {
                    analyseTypeSymbol(name, form.loc, typeVars)
                }
            }

            is VectorForm -> {
                val elForm = form.els.singleOrNull()
                    ?: return errorType("Vector type must have exactly one element type", form.loc)
                VectorType(analyseTypeForm(elForm, typeVars)).notNull()
            }

            is SetForm -> {
                val elForm = form.els.singleOrNull()
                    ?: return errorType("Set type must have exactly one element type", form.loc)
                SetType(analyseTypeForm(elForm, typeVars)).notNull()
            }

            is ListForm -> {
                val first = form.els.firstOrNull() as? SymbolForm
                    ?: return errorType("Type form must start with a symbol", form.loc)
                when (first.sym.name) {
                    "Iterable" -> {
                        val elForm = form.els.getOrNull(1)
                            ?: return errorType("Iterable type requires an element type", form.loc)
                        IterableType(analyseTypeForm(elForm, typeVars)).notNull()
                    }
                    "Iterator" -> {
                        val elForm = form.els.getOrNull(1)
                            ?: return errorType("Iterator type requires an element type", form.loc)
                        IteratorType(analyseTypeForm(elForm, typeVars)).notNull()
                    }
                    "Fn" -> {
                        val paramVec = form.els.getOrNull(1) as? VectorForm
                            ?: return errorType("Fn type requires a vector of parameter types", form.loc)
                        val retForm = form.els.getOrNull(2)
                            ?: return errorType("Fn type requires a return type", form.loc)
                        val paramTypes = paramVec.els.map { analyseTypeForm(it, typeVars) }
                        val returnType = analyseTypeForm(retForm, typeVars)
                        FnType(paramTypes, returnType).notNull()
                    }
                    else -> {
                        val args = form.els.drop(1).map { analyseTypeForm(it, typeVars) }
                        val invariantVariances = args.map { Variance.INVARIANT }

                        // Check if it's an enum type
                        val enumVariants = nsEnv.enums[first.sym] ?: ctx.brjCore.enums[first.sym]
                        if (enumVariants != null) {
                            return EnumType(first.sym.name, args, invariantVariances).notNull()
                        }

                        // Check if it's a tag name
                        val tagNs = nsEnv[first.sym]?.let { nsEnv.nsDecl?.name ?: "" }
                            ?: ctx.brjCore[first.sym]?.let { "brj.core" }
                        if (tagNs != null) {
                            return TagType(tagNs, first.sym.name, args, invariantVariances).notNull()
                        }

                        // Check if it's an import alias
                        val fqClass = nsEnv.imports[first.sym.name]
                        if (fqClass != null) {
                            return HostType(fqClass, args, invariantVariances).notNull()
                        }

                        errorType("Unsupported type constructor: ${first.sym.name}", form.loc)
                    }
                }
            }

            is RecordForm -> RecordType.notNull()

            else -> errorType("Unsupported type form", form.loc)
        }

    internal fun analyseTypeForm(form: Form): Type = analyseTypeForm(form, emptyMap())

    private fun analyseInteropDecl(forms: List<Form>, typeVars: Map<String, TypeVar>, loc: SourceSection?): Expr {
        val specForm = forms[0]
        val retForm = forms.getOrNull(1)
            ?: return errorExpr("interop decl missing return type", specForm.loc)

        val member = when {
            specForm is QSymbolForm -> {
                // I/EPOCH I — static field
                val returnType = analyseTypeForm(retForm, typeVars)
                InteropMember(
                    qualifiedName = "${specForm.ns.name}/${specForm.member.name}",
                    importAlias = specForm.ns.name,
                    memberName = specForm.member.name,
                    kind = InteropMemberKind.STATIC_FIELD,
                    declaredType = returnType,
                )
            }

            specForm is QDotSymbolForm -> {
                // Alias/.someField Int — instance field
                val fqClass = nsEnv.imports[specForm.ns.name]
                    ?: return errorExpr("Unknown import alias: ${specForm.ns.name}", specForm.loc)
                val receiverType = HostType(fqClass).notNull()
                val returnType = analyseTypeForm(retForm, typeVars)
                InteropMember(
                    qualifiedName = "${specForm.ns.name}/${specForm.member.name}",
                    importAlias = specForm.ns.name,
                    memberName = specForm.member.name,
                    kind = InteropMemberKind.INSTANCE_FIELD,
                    declaredType = FnType(listOf(receiverType), returnType).notNull(),
                )
            }

            specForm is ListForm -> {
                val callee = specForm.els.firstOrNull()
                    ?: return errorExpr("interop decl call must have a callee", specForm.loc)
                val paramTypes = specForm.els.drop(1).map { analyseTypeForm(it, typeVars) }
                val returnType = analyseTypeForm(retForm, typeVars)

                when {
                    callee is QSymbolForm -> {
                        // I/now() I or I/parse(Str) I — static method
                        InteropMember(
                            qualifiedName = "${callee.ns.name}/${callee.member.name}",
                            importAlias = callee.ns.name,
                            memberName = callee.member.name,
                            kind = InteropMemberKind.STATIC_METHOD,
                            declaredType = FnType(paramTypes, returnType).notNull(),
                        )
                    }

                    callee is QDotSymbolForm -> {
                        // Alias/.toEpochMilli() Int — instance method
                        val fqClass = nsEnv.imports[callee.ns.name]
                            ?: return errorExpr("Unknown import alias: ${callee.ns.name}", callee.loc)
                        val receiverType = HostType(fqClass).notNull()
                        InteropMember(
                            qualifiedName = "${callee.ns.name}/${callee.member.name}",
                            importAlias = callee.ns.name,
                            memberName = callee.member.name,
                            kind = InteropMemberKind.INSTANCE_METHOD,
                            declaredType = FnType(listOf(receiverType) + paramTypes, returnType).notNull(),
                        )
                    }

                    else -> return errorExpr("interop decl: unsupported call form", specForm.loc)
                }
            }

            else -> return errorExpr("interop decl: unsupported member form", specForm.loc)
        }

        return InteropDeclExpr(listOf(member), loc)
    }

    private fun analyseDecl(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1)
            ?: return errorExpr("decl requires a name", form.loc)

        return when {
            // batch keys — record sugar: decl: {.name Str, .age Int}
            sigForm is RecordForm -> {
                val names = (0 until sigForm.els.size step 2).map { i ->
                    val kw = sigForm.els[i] as? KeywordForm
                        ?: return errorExpr("record key decl entries must alternate keyword and type", sigForm.els[i].loc)
                    kw.sym
                }
                DefKeysExpr(names, form.loc)
            }

            // single key: decl: :name Str
            sigForm is KeywordForm -> {
                DefKeysExpr(listOf(sigForm.sym), form.loc)
            }

            // type variables: decl: [a] identity(a) a
            sigForm is VectorForm -> {
                val typeVarNames = sigForm.els.map { tvForm ->
                    (tvForm as? SymbolForm)?.sym?.name
                        ?: return errorExpr("type variable must be a symbol", tvForm.loc)
                }
                val typeVars = typeVarNames.associateWith { TypeVar() }
                analyseSingleDecl(els.drop(2), typeVars, form.loc)
            }

            // single decl without type vars
            else -> analyseSingleDecl(els.drop(1), emptyMap(), form.loc)
        }
    }

    private fun analyseSingleDecl(forms: List<Form>, typeVars: Map<String, TypeVar>, loc: SourceSection?): Expr {
        val sigForm = forms.firstOrNull()
            ?: return errorExpr("decl requires a signature", loc)

        fun isInteropForm(form: Form): Boolean = when {
            form is QSymbolForm -> true
            form is QDotSymbolForm -> true
            form is ListForm -> {
                val first = form.els.firstOrNull()
                first is QSymbolForm || first is QDotSymbolForm
            }
            else -> false
        }

        return when {
            isInteropForm(sigForm) -> analyseInteropDecl(forms, typeVars, loc)

            sigForm is ListForm -> {
                // decl: foo(Int, Str) Bool — function type declaration
                val nameForm = sigForm.els.firstOrNull() as? SymbolForm
                    ?: return errorExpr("decl signature must start with a name", sigForm.loc)
                val retForm = forms.getOrNull(1)
                    ?: return errorExpr("decl function requires a return type", loc)
                val paramTypes = sigForm.els.drop(1).map { analyseTypeForm(it, typeVars) }
                val returnType = analyseTypeForm(retForm, typeVars)
                DeclExpr(nameForm.sym, FnType(paramTypes, returnType).notNull(), loc)
            }

            sigForm is SymbolForm -> {
                // decl: x Int — value type declaration
                val typeForm = forms.getOrNull(1)
                    ?: return errorExpr("decl requires a type", loc)
                DeclExpr(sigForm.sym, analyseTypeForm(typeForm, typeVars), loc)
            }

            else -> errorExpr("decl requires a name or signature", loc)
        }
    }

    private fun analyseDef(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1)
            ?: return errorExpr("def requires a name", form.loc)

        val metaExpr = form.staticMeta?.let { analyseValueExpr(it) }

        return when (sigForm) {
            is ListForm -> {
                // def: foo(a, b) body -> define a function
                val name = (sigForm.els.firstOrNull() as? SymbolForm)?.sym
                    ?: return errorExpr("def signature must start with a name", sigForm.loc)
                // Reuse analyseFn by constructing: (fn (name params...) body...)
                val fnForm = ListForm(listOf(SymbolForm("fn".sym)) + els.drop(1), form.loc)
                val fnExpr = analyseFn(fnForm)
                DefExpr(name, fnExpr, metaExpr, form.loc)
            }
            is SymbolForm -> {
                // def: foo value -> define a value
                val name = sigForm.sym
                val valueForm = els.getOrNull(2)
                    ?: return errorExpr("def requires a value", form.loc)
                val valueExpr = analyseValueExpr(valueForm)
                DefExpr(name, valueExpr, metaExpr, form.loc)
            }
            else -> errorExpr("def requires a name or signature", form.loc)
        }
    }

    private fun analyseTag(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1)
            ?: return errorExpr("tag requires a tag signature", form.loc)

        // tag: [t] Just(t) — type variables via leading vector
        if (sigForm is VectorForm) {
            val typeVarNames = sigForm.els.map { tvForm ->
                (tvForm as? SymbolForm)?.sym?.name
                    ?: return errorExpr("type variable must be a symbol", tvForm.loc)
            }
            val innerSigForm = els.getOrNull(2)
                ?: return errorExpr("parameterised tag requires a signature after type vars", form.loc)
            return analyseTagSig(innerSigForm, typeVarNames, form.loc)
        }

        return analyseTagSig(sigForm, emptyList(), form.loc)
    }

    private fun analyseTagSig(sigForm: Form, typeVarNames: List<String>, loc: SourceSection?): Expr {
        return when (sigForm) {
            is SymbolForm -> {
                // tag: Nothing (nullary)
                val name = sigForm.sym
                if (!name.name[0].isUpperCase()) return errorExpr("tag names must be capitalized: ${name.name}", sigForm.loc)
                DefTagExpr(name, emptyList(), typeVarNames, loc = loc)
            }
            is ListForm -> {
                // tag: Just(t) or tag: [t] Just(t) or tag: Foo({:k1, :k2})
                val nameForm = sigForm.els.firstOrNull() as? SymbolForm
                    ?: return errorExpr("tag signature must start with a name", sigForm.loc)
                val name = nameForm.sym
                if (!name.name[0].isUpperCase()) return errorExpr("tag names must be capitalized: ${name.name}", nameForm.loc)

                val remainingEls = sigForm.els.drop(1)
                val singleRecord = remainingEls.singleOrNull() as? RecordForm
                if (singleRecord != null) {
                    // tag: Foo({:key1, :key2}) — each key becomes a field name and is registered as a key
                    val fieldNames = mutableListOf<String>()
                    for (recEl in singleRecord.els) {
                        val kw = recEl as? KeywordForm
                            ?: return errorExpr("record field entries must be keywords", recEl.loc)
                        fieldNames.add(kw.sym.name)
                    }
                    return DefTagExpr(name, fieldNames, typeVarNames, recordStyle = true, loc = loc)
                }

                val fieldNames = mutableListOf<String>()
                for (el in remainingEls) {
                    val sym = el as? SymbolForm
                        ?: return errorExpr("field names must be symbols", el.loc)
                    fieldNames.add(sym.sym.name)
                }
                DefTagExpr(name, fieldNames, typeVarNames, loc = loc)
            }
            else -> errorExpr("tag requires a tag name or signature", loc)
        }
    }

    private fun analyseEnum(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1)
            ?: return errorExpr("enum requires a name", form.loc)

        val (enumName, typeVarNames) = when (sigForm) {
            is SymbolForm -> sigForm.sym to emptyList<String>()
            is ListForm -> {
                val name = (sigForm.els.firstOrNull() as? SymbolForm)?.sym
                    ?: return errorExpr("enum name must be a symbol", sigForm.loc)
                val tvs = sigForm.els.drop(1).map { tvForm ->
                    (tvForm as? SymbolForm)?.sym?.name
                        ?: return errorExpr("type variable must be a symbol", tvForm.loc)
                }
                name to tvs
            }
            else -> return errorExpr("enum requires a name", sigForm.loc)
        }

        if (!enumName.name[0].isUpperCase()) return errorExpr("enum names must be capitalized: ${enumName.name}", sigForm.loc)

        val variants = mutableListOf<DefTagExpr>()
        for (nested in els.drop(2)) {
            if (nested !is ListForm) {
                errorExpr("enum variants must be tag: forms", nested.loc)
                continue
            }
            val first = nested.els.firstOrNull() as? SymbolForm
            if (first?.sym?.name != "tag") {
                errorExpr("enum variants must be tag: forms, got: ${first?.sym?.name}", nested.loc)
                continue
            }
            val tagSigForm = nested.els.getOrNull(1)
            if (tagSigForm == null) {
                errorExpr("tag requires a signature", nested.loc)
                continue
            }

            val tagExpr = analyseTagSig(tagSigForm, typeVarNames, nested.loc)
            if (tagExpr is DefTagExpr) {
                variants.add(tagExpr)
            }
        }

        if (variants.isEmpty()) return errorExpr("enum requires at least one variant", form.loc)

        return DefEnumExpr(enumName, typeVarNames, variants, form.loc)
    }

    private fun analyseDefMacro(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1) as? ListForm
            ?: return errorExpr("defmacro requires a signature: defmacro: name(params)", form.loc)

        val sigEls = sigForm.els
        val name = (sigEls.firstOrNull() as? SymbolForm)?.sym
            ?: return errorExpr("defmacro signature must start with a name", sigForm.loc)

        val params = mutableListOf<String>()
        var isVariadic = false
        val paramForms = sigEls.drop(1)
        for ((i, el) in paramForms.withIndex()) {
            val sym = el as? SymbolForm
                ?: return errorExpr("defmacro parameter must be a symbol", el.loc)
            if (sym.sym.name == "&") {
                val restSym = paramForms.getOrNull(i + 1) as? SymbolForm
                    ?: return errorExpr("& must be followed by a rest parameter name", el.loc)
                if (i + 2 < paramForms.size)
                    return errorExpr("& rest parameter must be the last parameter", paramForms[i + 2].loc)
                params.add(restSym.sym.name)
                isVariadic = true
                break
            }
            params.add(sym.sym.name)
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) return errorExpr("defmacro requires a body", form.loc)

        var macroAnalyser = Analyser(ctx, nsEnv, errors = errors, gensymScope = gensymScope)
        val paramLvs = mutableListOf<LocalVar>()
        for (param in params) {
            val (newAnalyser, lv) = macroAnalyser.withLocal(param)
            macroAnalyser = newAnalyser
            paramLvs.add(lv)
        }

        val bodyExpr = macroAnalyser.analyseBody(bodyForms, form.loc)

        return DefMacroExpr(name, FnExpr(name.name, paramLvs, bodyExpr, macroAnalyser.slotCount, emptyList(), isVariadic, form.loc), form.loc)
    }

    private fun analyseDefx(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1)
            ?: return errorExpr("defx requires a name", form.loc)

        // Two accepted signature shapes:
        //   defx: foo (Fn [Int] Int) default?            — original: name + full Fn type
        //   defx: foo(Int, Str?) Ret default?            — decl-style: call-shape + return type
        return when (sigForm) {
            is SymbolForm -> {
                val typeForm = els.getOrNull(2)
                    ?: return errorExpr("defx requires a type", form.loc)
                val type = analyseTypeForm(typeForm)
                val defaultExpr = els.getOrNull(3)?.let { analyseValueExpr(it) }
                DefxExpr(sigForm.sym, type, defaultExpr, form.loc)
            }

            is ListForm -> {
                val nameForm = sigForm.els.firstOrNull() as? SymbolForm
                    ?: return errorExpr("defx signature must start with a name", sigForm.loc)
                val retForm = els.getOrNull(2)
                    ?: return errorExpr("defx requires a return type", form.loc)
                val paramTypes = sigForm.els.drop(1).map { analyseTypeForm(it) }
                val returnType = analyseTypeForm(retForm)
                val type = FnType(paramTypes, returnType).notNull()
                val defaultExpr = els.getOrNull(3)?.let { analyseValueExpr(it) }
                DefxExpr(nameForm.sym, type, defaultExpr, form.loc)
            }

            else -> errorExpr("defx requires a name or call-shaped signature", sigForm.loc)
        }
    }

    private fun analyseLang(form: ListForm): ValueExpr {
        val els = form.els
        val langForm = els.getOrNull(1) as? StringForm
            ?: return errorExpr("lang requires a language name as a string literal", form.loc)
        val typeForm = els.getOrNull(2)
            ?: return errorExpr("lang requires a declared type", form.loc)
        val codeForm = els.getOrNull(3) as? StringForm
            ?: return errorExpr("lang requires a code string", form.loc)

        val available = ctx.truffleEnv.publicLanguages.keys
        if (langForm.value !in available) {
            return errorExpr(
                "Language '${langForm.value}' is not available. Available: ${available.sorted()}",
                langForm.loc,
            )
        }

        return LangExpr(langForm.value, analyseTypeForm(typeForm), codeForm.value, form.loc)
    }

    private fun analyseWithFx(form: ListForm): ValueExpr {
        val els = form.els
        val bindingsForm = els.getOrNull(1) as? VectorForm
            ?: return errorExpr("withFx requires a vector of bindings", form.loc)
        val bindingEls = bindingsForm.els
        if (bindingEls.size % 2 != 0)
            return errorExpr("withFx bindings must have even number of forms", form.loc)

        val bindings = mutableListOf<Pair<GlobalVar, ValueExpr>>()
        for (i in bindingEls.indices step 2) {
            val nameForm = bindingEls[i]
            val effectVar = when (nameForm) {
                is SymbolForm -> nsEnv.effectVar(nameForm.sym) ?: ctx.brjCore.effectVar(nameForm.sym)
                is QSymbolForm -> ctx.namespaces[nameForm.ns.name]?.effectVar(nameForm.member)
                    ?: nsEnv.requires[nameForm.ns.name]?.effectVar(nameForm.member)
                else -> return errorExpr("withFx binding name must be a symbol", nameForm.loc)
            } ?: return errorExpr("Unknown effect: $nameForm", nameForm.loc)
            bindings.add(effectVar to analyseValueExpr(bindingEls[i + 1]))
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) return errorExpr("withFx requires a body", form.loc)
        val bodyExpr = analyseBody(bodyForms, form.loc)

        return WithFxExpr(bindings, bodyExpr, form.loc)
    }

    fun analyseExpr(form: Form): Expr {
        if (form is ListForm) {
            val first = form.els.firstOrNull()
            if (first is SymbolForm) {
                when (first.sym.name) {
                    "do" -> return TopLevelDo(form.els.drop(1), form.loc)
                    "def" -> return analyseDef(form)
                    "decl" -> return analyseDecl(form)
                    "tag" -> return analyseTag(form)
                    "enum" -> return analyseEnum(form)
                    "defmacro" -> return analyseDefMacro(form)
                    "defkeys" -> return errorExpr("defkeys has been replaced: use decl: .name Str", form.loc)
                    "defx" -> return analyseDefx(form)
                }
            }
        }

        return analyseValueExpr(form)
    }

    fun analyse(form: Form): Expr = 
        analyseExpr(form)
            .takeIf { errors.isEmpty() }
            ?: AnalyserErrors(form.loc, errors.toList())
}
