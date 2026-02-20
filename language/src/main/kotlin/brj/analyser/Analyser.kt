package brj.analyser

import brj.*
import brj.Result
import brj.runtime.BridjeContext
import brj.runtime.BridjeKey
import brj.runtime.BridjeMacro
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

data class Analyser(
    private val ctx: BridjeContext,
    private val nsEnv: NsEnv = NsEnv(),
    private val locals: Map<String, LocalVar> = emptyMap(),
    private val nextSlot: AtomicInteger = AtomicInteger(0),
    private val expansionDepth: Int = 0,
    private val errors: MutableList<Error> = mutableListOf(),
    private val gensymScope: MutableMap<String, String> = mutableMapOf(),
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

    fun errorExpr(
        message: String,
        loc: SourceSection? = null,
    ): ValueExpr {
        errors.add(Error(message, loc))
        return ErrorValueExpr(message, loc = loc)
    }

    private fun tryHostLookup(name: String, loc: SourceSection?): TruffleObject? =
        try {
            ctx.truffleEnv.lookupHostSymbol(name.replace(':', '.')) as TruffleObject
        } catch (_: Exception) {
            null
        }

    private fun analyseSymbol(form: SymbolForm): ValueExpr =
        when (form.name) {
            "nil" -> NilExpr(form.loc)
            "true" -> BoolExpr(true, form.loc)
            "false" -> BoolExpr(false, form.loc)

            else -> locals[form.name]?.let { LocalVarExpr(it, form.loc) }
                ?: nsEnv[form.name]?.let { GlobalVarExpr(it, form.loc) }
                ?: ctx.brjCore[form.name]?.let { GlobalVarExpr(it, form.loc) }
                ?: nsEnv.imports[form.name]?.let { fqClass ->
                    tryHostLookup(fqClass, form.loc)?.let { HostConstructorExpr(it, form.loc) }
                        ?: errorExpr("Imported class not found: $fqClass", form.loc)
                }
                ?: tryHostLookup(form.name, form.loc)?.let { HostConstructorExpr(it, form.loc) }
                ?: errorExpr("Unknown symbol: ${form.name}", form.loc)
        }

    private fun resolveKey(name: String): GlobalVar? {
        val idx = name.lastIndexOf(':')
        if (idx >= 0) {
            val nsAlias = name.substring(0, idx)
            val member = name.substring(idx + 1)
            ctx.namespaces[nsAlias]?.key(member)?.let { return it }
            nsEnv.requires[nsAlias]?.key(member)?.let { return it }
            return null
        }
        return nsEnv.key(name) ?: ctx.brjCore.key(name)
    }

    private fun analyseKeyword(form: KeywordForm): ValueExpr =
        resolveKey(form.name)?.let { GlobalVarExpr(it, form.loc) }
            ?: errorExpr("Unknown key: :${form.name}", form.loc)

    private fun analyseRecord(form: RecordForm): ValueExpr {
        val els = form.els

        if (els.size % 2 != 0) return errorExpr("record literal must have even number of forms", form.loc)

        val fields = mutableListOf<Pair<String, ValueExpr>>()

        for (i in els.indices step 2) {
            val keyForm = els[i] as? KeywordForm
                ?: return errorExpr("record keys must be keywords", els[i].loc)
            val keyValue = resolveKey(keyForm.name)?.value
            if (keyValue !is BridjeKey) {
                return errorExpr(":${keyForm.name} is not a key", els[i].loc)
            }
            val valueExpr = analyseValueExpr(els[i + 1])
            fields.add(keyValue.name to valueExpr)
        }

        return RecordExpr(fields, form.loc)
    }

    private fun analyseQualifiedSymbol(form: QualifiedSymbolForm): ValueExpr {
        // Try Bridje namespace first (fully qualified)
        ctx.namespaces[form.namespace]?.let { ns ->
            val globalVar = ns[form.member]
                ?: return errorExpr("Unknown symbol: ${form.member} in namespace ${form.namespace}", form.loc)
            return GlobalVarExpr(globalVar, form.loc)
        }

        // Check if namespace is a require alias
        nsEnv.requires[form.namespace]?.let { ns ->
            val globalVar = ns[form.member]
                ?: return errorExpr("Unknown symbol: ${form.member} in required namespace ${form.namespace}", form.loc)
            return GlobalVarExpr(globalVar, form.loc)
        }

        val fqClass = nsEnv.imports[form.namespace] ?: form.namespace.replace(':', '.')

        val hostClass =
            try {
                ctx.truffleEnv.lookupHostSymbol(fqClass.replace(':', '.')) as TruffleObject
            } catch (_: Exception) {
                // Namespace lookup failed - try the full qualified name as a Java class
                // (e.g., java:lang:String where namespace=java:lang, member=String)
                val fullName = "${form.namespace}:${form.member}"
                tryHostLookup(fullName, form.loc)?.let { return HostConstructorExpr(it, form.loc) }
                return errorExpr("Unknown namespace: ${form.namespace}", form.loc)
            }

        return HostStaticMethodExpr(hostClass, form.member, form.loc)
    }

    private fun analyseListValueExpr(form: ListForm): ValueExpr {
        val els = form.els
        val first = els.firstOrNull() ?: TODO("empty list not supported yet")

        return when (first) {
            is SymbolForm -> when (first.name) {
                "let" -> analyseLet(form)
                "fn" -> analyseFn(form)
                "do" -> analyseDo(form)
                "if" -> analyseIf(form)
                "case" -> analyseCase(form)
                "quote" -> analyseQuote(form)
                "def" -> errorExpr("def not allowed in value position", form.loc)
                "deftag" -> errorExpr("deftag not allowed in value position", form.loc)
                "defmacro" -> errorExpr("defmacro not allowed in value position", form.loc)
                "defkey" -> errorExpr("defkey not allowed in value position", form.loc)
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
        val constructor = nsEnv[name] ?: ctx.brjCore[name]
            ?: return errorExpr("$name constructor not found", loc)
        return CallExpr(GlobalVarExpr(constructor, loc), args, loc)
    }

    private fun collFormConstructor(name: String, els: List<Form>, loc: SourceSection?): ValueExpr {
        val elements = els.map { analyseQuotedForm(it) }
        return callFormConstructor(name, listOf(VectorExpr(elements, loc)), loc)
    }

    private fun analyseQuotedForm(form: Form): ValueExpr =
        when (form) {
            is UnquoteForm -> analyseValueExpr(form.form)

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
                val name = if (form.name.endsWith("#")) {
                    val baseName = form.name.dropLast(1)
                    gensymScope.getOrPut(baseName) { "${baseName}__${ctx.nextGensymId()}" }
                } else {
                    form.name
                }
                callFormConstructor("Symbol", listOf(StringExpr(name, form.loc)), form.loc)
            }

            is KeywordForm ->
                callFormConstructor("Keyword", listOf(StringExpr(form.name, form.loc)), form.loc)

            is QualifiedSymbolForm -> {
                val member = if (form.member.endsWith("#")) {
                    val baseName = form.member.dropLast(1)
                    gensymScope.getOrPut(baseName) { "${baseName}__${ctx.nextGensymId()}" }
                } else {
                    form.member
                }
                callFormConstructor(
                    "QualifiedSymbol",
                    listOf(
                        StringExpr(form.namespace, form.loc),
                        StringExpr(member, form.loc)
                    ),
                    form.loc
                )
            }

            is ListForm -> collFormConstructor("List", form.els, form.loc)
            is VectorForm -> collFormConstructor("Vector", form.els, form.loc)
            is SetForm -> collFormConstructor("Set", form.els, form.loc)
            is RecordForm -> collFormConstructor("Record", form.els, form.loc)
        }

    private fun analyseQuote(form: ListForm): ValueExpr {
        if (form.els.size != 2) return errorExpr("quote requires exactly one argument", form.loc)
        return analyseQuotedForm(form.els[1])
    }

    private fun analyseIf(form: ListForm): ValueExpr {
        val els = form.els
        if (els.size != 4) return errorExpr("if requires exactly 3 arguments: predicate, then, else", form.loc)

        return IfExpr(
            analyseValueExpr(els[1]),
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

        val scrutinee = analyseValueExpr(els[1])
        val branchForms = els.drop(2)

        val branches = mutableListOf<CaseBranch>()
        var i = 0
        while (i < branchForms.size) {
            val patternForm = branchForms[i]

            // Check if this looks like a pattern (capitalized symbol, nil, lowercase symbol followed by body, or call with capitalized symbol)
            val isPattern = when (patternForm) {
                is SymbolForm -> patternForm.name[0].isUpperCase() || patternForm.name == "nil" ||
                    (patternForm.name[0].isLowerCase() && i + 1 < branchForms.size)
                is ListForm -> {
                    val first = patternForm.els.firstOrNull()
                    first is SymbolForm && first.name[0].isUpperCase()
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

        return CaseExpr(scrutinee, branches, form.loc)
    }

    private fun resolveTag(name: String, loc: SourceSection?): Result<Error, Any> {
        val globalVar = nsEnv[name] ?: ctx.brjCore[name]
            ?: return Result.Err(Error("Unknown tag: $name", loc))
        val value = globalVar.value
            ?: return Result.Err(Error("Tag $name has no value", loc))
        return Result.Ok(value)
    }

    private fun analyseBindingNames(els: List<Form>): Result<Error, List<String>> {
        val names = mutableListOf<String>()
        for (el in els) {
            val sym = el as? SymbolForm
                ?: return Result.Err(Error("case pattern bindings must be symbols", el.loc))
            names.add(sym.name)
        }
        return Result.Ok(names)
    }

    private fun analyseCaseBranch(patternForm: Form, bodyForm: Form): Result<Error, CaseBranch> =
        when (patternForm) {
            is SymbolForm -> {
                val name = patternForm.name
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

            is ListForm -> {
                val tagForm = patternForm.els.firstOrNull() as? SymbolForm
                if (tagForm == null) {
                    Result.Err(Error("case pattern must start with a tag name", patternForm.loc))
                } else {
                    val tagName = tagForm.name
                    if (!tagName[0].isUpperCase()) {
                        Result.Err(Error("case pattern tag must be capitalized: $tagName", tagForm.loc))
                    } else {
                        resolveTag(tagName, tagForm.loc).flatMap { tagValue ->
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
                }
            }
            else -> Result.Err(Error("case pattern must be a tag", patternForm.loc))
        }

    private fun analyseBody(forms: List<Form>, loc: SourceSection?): ValueExpr =
        when {
            forms.isEmpty() -> errorExpr("body requires at least one expression", loc)
            forms.size == 1 -> analyseValueExpr(forms[0])
            else -> {
                val sideEffects = forms.dropLast(1).map { analyseValueExpr(it) }
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

        val bindingExpr = analyseValueExpr(valueForm)
        val (newAnalyser, localVar) = withLocal(nameForm.name)

        val bodyExpr = newAnalyser.analyseBindings(bindingEls.drop(2), bodyForms, loc)

        return LetExpr(localVar, bindingExpr, bodyExpr, loc)
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

    private fun analyseFn(form: ListForm): ValueExpr {
        val els = form.els
        val sigForm = els.getOrNull(1) as? ListForm
            ?: return errorExpr("fn requires a signature list (fn-name & params)", form.loc)

        val sigEls = sigForm.els
        val fnName = (sigEls.firstOrNull() as? SymbolForm)?.name
            ?: return errorExpr("fn signature must start with a name", sigForm.loc)

        val params = mutableListOf<String>()
        for (el in sigEls.drop(1)) {
            val sym = el as? SymbolForm
                ?: return errorExpr("fn parameter must be a symbol", el.loc)
            params.add(sym.name)
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) return errorExpr("fn requires a body", form.loc)

        var fnAnalyser = Analyser(ctx, nsEnv, errors = errors, gensymScope = gensymScope)
        for (param in params) {
            val (newAnalyser, _) = fnAnalyser.withLocal(param)
            fnAnalyser = newAnalyser
        }

        val bodyExpr = fnAnalyser.analyseBody(bodyForms, form.loc)

        return FnExpr(fnName, params, bodyExpr, fnAnalyser.slotCount, form.loc)
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
                val args = els.drop(1).toTypedArray<Any>()
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
            is QualifiedSymbolForm -> analyseQualifiedSymbol(form)
            is ListForm -> analyseListValueExpr(form)
            is VectorForm -> VectorExpr(form.els.map { analyseValueExpr(it) }, form.loc)
            is SetForm -> SetExpr(form.els.map { analyseValueExpr(it) }, form.loc)
            is RecordForm -> analyseRecord(form)
            is UnquoteForm -> errorExpr("unquote (~) can only be used inside a quote", form.loc)
        }
    }

    fun analyseValueExpr(form: Form): ValueExpr {
        val inner = analyseValueExprInner(form)
        val meta = form.meta ?: return inner

        val withMetaVar = ctx.brjCore["withMeta"]
            ?: return errorExpr("withMeta not found in brj.core", form.loc)

        return CallExpr(
            GlobalVarExpr(withMetaVar, form.loc),
            listOf(inner, analyseValueExpr(meta)),
            form.loc
        )
    }

    private fun analyseDef(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1)
            ?: return errorExpr("def requires a name", form.loc)

        val metaExpr = form.meta?.let { analyseValueExpr(it) }

        return when (sigForm) {
            is ListForm -> {
                // def: foo(a, b) body -> define a function
                val name = (sigForm.els.firstOrNull() as? SymbolForm)?.name
                    ?: return errorExpr("def signature must start with a name", sigForm.loc)
                // Reuse analyseFn by constructing: (fn (name params...) body...)
                val fnForm = ListForm(listOf(SymbolForm("fn")) + els.drop(1), form.loc)
                val fnExpr = analyseFn(fnForm)
                DefExpr(name, fnExpr, metaExpr, form.loc)
            }
            is SymbolForm -> {
                // def: foo value -> define a value
                val name = sigForm.name
                val valueForm = els.getOrNull(2)
                    ?: return errorExpr("def requires a value", form.loc)
                val valueExpr = analyseValueExpr(valueForm)
                DefExpr(name, valueExpr, metaExpr, form.loc)
            }
            else -> errorExpr("def requires a name or signature", form.loc)
        }
    }

    private fun analyseDefTag(form: ListForm): Expr {
        val sigForm = form.els.getOrNull(1)
            ?: return errorExpr("deftag requires a tag signature", form.loc)

        return when (sigForm) {
            is SymbolForm -> {
                // deftag: Nothing (nullary)
                val name = sigForm.name
                if (!name[0].isUpperCase()) return errorExpr("tag names must be capitalized: $name", sigForm.loc)
                DefTagExpr(name, emptyList(), form.loc)
            }
            is ListForm -> {
                // deftag: Just(value)
                val nameForm = sigForm.els.firstOrNull() as? SymbolForm
                    ?: return errorExpr("deftag signature must start with a name", sigForm.loc)
                val name = nameForm.name
                if (!name[0].isUpperCase()) return errorExpr("tag names must be capitalized: $name", nameForm.loc)
                val fieldNames = mutableListOf<String>()
                for (el in sigForm.els.drop(1)) {
                    val sym = el as? SymbolForm
                        ?: return errorExpr("field names must be symbols", el.loc)
                    fieldNames.add(sym.name)
                }
                DefTagExpr(name, fieldNames, form.loc)
            }
            else -> errorExpr("deftag requires a tag name or signature", form.loc)
        }
    }

    private fun analyseDefKey(form: ListForm): Expr {
        val els = form.els
        val keyKeyword = els.getOrNull(1) as? KeywordForm
            ?: return errorExpr("defkey requires a keyword: defkey: :name Type", form.loc)
        return DefKeyExpr(keyKeyword.name, form.loc)
    }

    private fun analyseDefMacro(form: ListForm): Expr {
        val els = form.els
        val sigForm = els.getOrNull(1) as? ListForm
            ?: return errorExpr("defmacro requires a signature: defmacro: name(params)", form.loc)

        val sigEls = sigForm.els
        val name = (sigEls.firstOrNull() as? SymbolForm)?.name
            ?: return errorExpr("defmacro signature must start with a name", sigForm.loc)

        val params = mutableListOf<String>()
        for (el in sigEls.drop(1)) {
            val sym = el as? SymbolForm
                ?: return errorExpr("defmacro parameter must be a symbol", el.loc)
            params.add(sym.name)
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) return errorExpr("defmacro requires a body", form.loc)

        var macroAnalyser = Analyser(ctx, nsEnv, errors = errors, gensymScope = gensymScope)
        for (param in params) {
            val (newAnalyser, _) = macroAnalyser.withLocal(param)
            macroAnalyser = newAnalyser
        }

        val bodyExpr = macroAnalyser.analyseBody(bodyForms, form.loc)

        return DefMacroExpr(name, FnExpr(name, params, bodyExpr, macroAnalyser.slotCount, form.loc), form.loc)
    }

    fun analyseExpr(form: Form): Expr {
        if (form is ListForm) {
            val first = form.els.firstOrNull()
            if (first is SymbolForm) {
                when (first.name) {
                    "do" -> return TopLevelDo(form.els.drop(1), form.loc)
                    "def" -> return analyseDef(form)
                    "deftag" -> return analyseDefTag(form)
                    "defmacro" -> return analyseDefMacro(form)
                    "defkey" -> return analyseDefKey(form)
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
