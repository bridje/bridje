package brj

import brj.runtime.HostClass
import brj.runtime.BridjeMacro
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.source.SourceSection
import java.util.concurrent.atomic.AtomicInteger

class Analyser(
    private val truffleEnv: TruffleLanguage.Env,
    private val globalEnv: GlobalEnv = GlobalEnv(),
    private val locals: Map<String, LocalVar> = emptyMap(),
    private val nextSlot: AtomicInteger = AtomicInteger(0),
    private val expansionDepth: Int = 0
) {
    val slotCount: Int get() = nextSlot.get()

    companion object {
        private const val MAX_EXPANSION_DEPTH = 100
    }

    internal fun withLocal(name: String): Pair<Analyser, LocalVar> {
        val lv = LocalVar(name, nextSlot.getAndIncrement())
        return Analyser(truffleEnv, globalEnv, locals + (name to lv), nextSlot, expansionDepth) to lv
    }

    fun analyseExpr(form: Form): Expr {
        if (form is ListForm) {
            val first = form.els.firstOrNull()
            if (first is SymbolForm) {
                when (first.name) {
                    "def" -> return analyseDef(form)
                    "deftag" -> return analyseDefTag(form)
                    "defmacro" -> return analyseDefMacro(form)
                    "defkey" -> return analyseDefKey(form)
                }
            }
        }
        return analyseValueExpr(form)
    }

    fun analyseValueExpr(form: Form): ValueExpr {
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
            is UnquoteForm -> error("unquote (~) can only be used inside a quote")
        }
    }

    private fun analyseSymbol(form: SymbolForm): ValueExpr {
        return when (form.name) {
            "nil" -> NilExpr(form.loc)
            "true" -> BoolExpr(true, form.loc)
            "false" -> BoolExpr(false, form.loc)
            else -> locals[form.name]?.let { LocalVarExpr(it, form.loc) }
                ?: globalEnv[form.name]?.let { GlobalVarExpr(it, form.loc) }
                ?: tryHostLookup(form.name, form.loc)?.let { HostConstructorExpr(it, form.loc) }
                ?: error("Unknown symbol: ${form.name}")
        }
    }

    private fun analyseKeyword(form: KeywordForm): ValueExpr {
        val key = globalEnv.getKey(form.name)
            ?: error("Unknown key: :${form.name}")
        return TruffleObjectExpr(key, form.loc)
    }

    private fun analyseRecord(form: RecordForm): ValueExpr {
        val els = form.els
        if (els.size % 2 != 0) {
            error("record literal must have even number of forms")
        }
        val fields = mutableListOf<Pair<String, ValueExpr>>()
        for (i in els.indices step 2) {
            val keyForm = els[i] as? KeywordForm
                ?: error("record keys must be keywords")
            val valueExpr = analyseValueExpr(els[i + 1])
            fields.add(keyForm.name to valueExpr)
        }
        return RecordExpr(fields, form.loc)
    }

    private fun analyseQualifiedSymbol(form: QualifiedSymbolForm): ValueExpr {
        val className = form.namespace.replace(':', '.')
        val hostClass = 
            try {
                truffleEnv.lookupHostSymbol(className) as TruffleObject
            } catch (_: Exception) {
                error("Unknown namespace: ${form.namespace}")
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
                "def" -> error("def not allowed in value position")
                "deftag" -> error("deftag not allowed in value position")
                "defmacro" -> error("defmacro not allowed in value position")
                "defkey" -> error("defkey not allowed in value position")
                else -> analyseCall(form)
            }
            else -> analyseCall(form)
        }
    }

    private fun analyseDo(form: ListForm): ValueExpr {
        val bodyForms = form.els.drop(1)
        if (bodyForms.isEmpty()) error("do requires at least one expression")
        return analyseBody(bodyForms, form.loc)
    }

    private fun analyseQuote(form: ListForm): ValueExpr {
        if (form.els.size != 2) error("quote requires exactly one argument")
        return analyseQuotedForm(form.els[1])
    }

    private fun analyseQuotedForm(form: Form): ValueExpr {
        return when (form) {
            is UnquoteForm -> {
                // Unquote: evaluate the inner form to get a Form at runtime
                analyseValueExpr(form.form)
            }
            is IntForm -> {
                // Generate: Int(42)
                callFormConstructor("Int", listOf(IntExpr(form.value, form.loc)), form.loc)
            }
            is DoubleForm -> {
                callFormConstructor("Double", listOf(DoubleExpr(form.value, form.loc)), form.loc)
            }
            is BigIntForm -> {
                callFormConstructor("BigInt", listOf(BigIntExpr(form.value, form.loc)), form.loc)
            }
            is BigDecForm -> {
                callFormConstructor("BigDec", listOf(BigDecExpr(form.value, form.loc)), form.loc)
            }
            is StringForm -> {
                callFormConstructor("String", listOf(StringExpr(form.value, form.loc)), form.loc)
            }
            is SymbolForm -> {
                // Generate: Symbol("name")
                callFormConstructor("Symbol", listOf(StringExpr(form.name, form.loc)), form.loc)
            }
            is QualifiedSymbolForm -> {
                // Generate: QualifiedSymbol("ns", "member")
                callFormConstructor(
                    "QualifiedSymbol",
                    listOf(
                        StringExpr(form.namespace, form.loc),
                        StringExpr(form.member, form.loc)
                    ),
                    form.loc
                )
            }
            is KeywordForm -> {
                callFormConstructor("Keyword", listOf(StringExpr(form.name, form.loc)), form.loc)
            }
            is ListForm -> {
                // Generate: List([el1, el2, ...])
                val elements = form.els.map { analyseQuotedForm(it) }
                val vectorExpr = VectorExpr(elements, form.loc)
                callFormConstructor("List", listOf(vectorExpr), form.loc)
            }
            is VectorForm -> {
                // Generate: Vector([el1, el2, ...])
                val elements = form.els.map { analyseQuotedForm(it) }
                val vectorExpr = VectorExpr(elements, form.loc)
                callFormConstructor("Vector", listOf(vectorExpr), form.loc)
            }
            is SetForm -> {
                val elements = form.els.map { analyseQuotedForm(it) }
                val vectorExpr = VectorExpr(elements, form.loc)
                callFormConstructor("Set", listOf(vectorExpr), form.loc)
            }
            is RecordForm -> {
                val elements = form.els.map { analyseQuotedForm(it) }
                val vectorExpr = VectorExpr(elements, form.loc)
                callFormConstructor("Record", listOf(vectorExpr), form.loc)
            }
        }
    }

    private fun callFormConstructor(name: String, args: List<ValueExpr>, loc: SourceSection?): ValueExpr {
        val constructor = globalEnv[name] ?: error("$name constructor not found")
        return CallExpr(GlobalVarExpr(constructor, loc), args, loc)
    }

    private fun analyseIf(form: ListForm): IfExpr {
        val els = form.els
        if (els.size != 4) error("if requires exactly 3 arguments: predicate, then, else")
        val predExpr = analyseValueExpr(els[1])
        val thenExpr = analyseValueExpr(els[2])
        val elseExpr = analyseValueExpr(els[3])
        return IfExpr(predExpr, thenExpr, elseExpr, form.loc)
    }

    private fun analyseDef(form: ListForm): DefExpr {
        val els = form.els
        val sigForm = els.getOrNull(1) ?: error("def requires a name")

        return when (sigForm) {
            is ListForm -> {
                // def: foo(a, b) body -> define a function
                val name = (sigForm.els.firstOrNull() as? SymbolForm)?.name
                    ?: error("def signature must start with a name")
                // Reuse analyseFn by constructing: (fn (name params...) body...)
                val fnForm = ListForm(listOf(SymbolForm("fn")) + els.drop(1), form.loc)
                val fnExpr = analyseFn(fnForm)
                DefExpr(name, fnExpr, form.loc)
            }
            is SymbolForm -> {
                // def: foo value -> define a value
                val name = sigForm.name
                val valueExpr = analyseValueExpr(els.getOrNull(2) ?: error("def requires a value"))
                DefExpr(name, valueExpr, form.loc)
            }
            else -> error("def requires a name or signature")
        }
    }

    private fun analyseDefTag(form: ListForm): DefTagExpr {
        val sigForm = form.els.getOrNull(1) ?: error("deftag requires a tag signature")

        return when (sigForm) {
            is SymbolForm -> {
                // deftag: Nothing (nullary)
                val name = sigForm.name
                if (!name[0].isUpperCase()) error("tag names must be capitalized: $name")
                DefTagExpr(name, emptyList(), form.loc)
            }
            is ListForm -> {
                // deftag: Just(value)
                val nameForm = sigForm.els.firstOrNull() as? SymbolForm
                    ?: error("deftag signature must start with a name")
                val name = nameForm.name
                if (!name[0].isUpperCase()) error("tag names must be capitalized: $name")
                val fieldNames = sigForm.els.drop(1).map {
                    (it as? SymbolForm)?.name ?: error("field names must be symbols")
                }
                DefTagExpr(name, fieldNames, form.loc)
            }
            else -> error("deftag requires a tag name or signature")
        }
    }

    private fun analyseDefKey(form: ListForm): DefKeyExpr {
        val els = form.els
        val keywordForm = els.getOrNull(1) as? KeywordForm
            ?: error("defkey requires a keyword: defkey: :name")
        // Third element (type) is ignored for now
        return DefKeyExpr(keywordForm.name, form.loc)
    }

    private fun analyseDefMacro(form: ListForm): DefMacroExpr {
        val els = form.els
        val sigForm = els.getOrNull(1) as? ListForm
            ?: error("defmacro requires a signature: defmacro: name(params)")

        val sigEls = sigForm.els
        val name = (sigEls.firstOrNull() as? SymbolForm)?.name
            ?: error("defmacro signature must start with a name")

        val params = sigEls.drop(1).map {
            (it as? SymbolForm)?.name ?: error("defmacro parameter must be a symbol")
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) error("defmacro requires a body")

        var macroAnalyser = Analyser(truffleEnv, globalEnv = globalEnv)
        for (param in params) {
            val (newAnalyser, _) = macroAnalyser.withLocal(param)
            macroAnalyser = newAnalyser
        }

        val bodyExpr = macroAnalyser.analyseBody(bodyForms, form.loc)

        return DefMacroExpr(name, FnExpr(name, params, bodyExpr, form.loc), form.loc)
    }

    private fun analyseCase(form: ListForm): CaseExpr {
        val els = form.els
        if (els.size < 3) error("case requires a scrutinee and at least one branch")

        val scrutinee = analyseValueExpr(els[1])
        val branchForms = els.drop(2)

        val branches = mutableListOf<CaseBranch>()
        var i = 0
        while (i < branchForms.size) {
            val patternForm = branchForms[i]

            // Check if this looks like a pattern (capitalized symbol or call with capitalized symbol)
            val isPattern = when (patternForm) {
                is SymbolForm -> patternForm.name[0].isUpperCase()
                is ListForm -> {
                    val first = patternForm.els.firstOrNull()
                    first is SymbolForm && first.name[0].isUpperCase()
                }
                else -> false
            }

            if (isPattern) {
                val bodyForm = branchForms.getOrNull(i + 1)
                    ?: error("case branch missing body expression")
                val branch = analyseCaseBranch(patternForm, bodyForm)
                branches.add(branch)
                i += 2
            } else {
                // Last form is not a pattern - treat as default
                if (i != branchForms.size - 1) {
                    error("default expression must be last in case")
                }
                val bodyExpr = analyseValueExpr(patternForm)
                branches.add(CaseBranch(DefaultPattern(patternForm.loc), bodyExpr, patternForm.loc))
                i += 1
            }
        }

        if (branches.isEmpty()) error("case requires at least one branch")

        return CaseExpr(scrutinee, branches, form.loc)
    }

    private fun resolveTag(name: String, loc: SourceSection?): Any {
        val globalVar = globalEnv[name] ?: error("Unknown tag: $name")
        return globalVar.value ?: error("Tag $name has no value")
    }

    private fun analyseCaseBranch(patternForm: Form, bodyForm: Form): CaseBranch {
        return when (patternForm) {
            is SymbolForm -> {
                val name = patternForm.name
                if (name[0].isUpperCase()) {
                    // Nullary tag pattern like Nothing
                    val tagValue = resolveTag(name, patternForm.loc)
                    val bodyExpr = analyseValueExpr(bodyForm)
                    CaseBranch(TagPattern(tagValue, emptyList(), patternForm.loc), bodyExpr, patternForm.loc)
                } else {
                    error("case pattern must be a tag (capitalized): $name")
                }
            }
            is ListForm -> {
                // Tag pattern with bindings like Just(x) or Pair(a, b)
                val tagForm = patternForm.els.firstOrNull() as? SymbolForm
                    ?: error("case pattern must start with a tag name")
                val tagName = tagForm.name
                if (!tagName[0].isUpperCase()) {
                    error("case pattern tag must be capitalized: $tagName")
                }
                val tagValue = resolveTag(tagName, tagForm.loc)

                val bindingNames = patternForm.els.drop(1).map {
                    (it as? SymbolForm)?.name
                        ?: error("case pattern bindings must be symbols")
                }

                // Create analyser with bindings in scope for body
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
            else -> error("case pattern must be a tag")
        }
    }

    private fun analyseBody(forms: List<Form>, loc: SourceSection?): ValueExpr {
        return when {
            forms.isEmpty() -> error("body requires at least one expression")
            forms.size == 1 -> analyseValueExpr(forms[0])
            else -> {
                val sideEffects = forms.dropLast(1).map { analyseValueExpr(it) }
                val result = analyseValueExpr(forms.last())
                DoExpr(sideEffects, result, loc)
            }
        }
    }

    private fun analyseLet(form: ListForm): ValueExpr {
        val els = form.els
        val bindingsForm = els.getOrNull(1) as? VectorForm
            ?: error("let requires a vector of bindings")

        val bindingEls = bindingsForm.els
        if (bindingEls.size % 2 != 0) {
            error("let bindings must have even number of forms")
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) {
            error("let requires a body")
        }

        return analyseBindings(bindingEls, bodyForms, form.loc)
    }

    private fun analyseBindings(
        bindingEls: List<Form>,
        bodyForms: List<Form>,
        loc: SourceSection?
    ): ValueExpr {
        if (bindingEls.isEmpty()) {
            return analyseBody(bodyForms, loc)
        }

        val nameForm = bindingEls[0] as? SymbolForm
            ?: error("binding name must be a symbol")
        val valueForm = bindingEls[1]

        val bindingExpr = analyseValueExpr(valueForm)
        val (newAnalyser, localVar) = withLocal(nameForm.name)

        val bodyExpr = newAnalyser.analyseBindings(bindingEls.drop(2), bodyForms, loc)

        return LetExpr(localVar, bindingExpr, bodyExpr, loc)
    }

    private fun analyseFn(form: ListForm): FnExpr {
        val els = form.els
        val sigForm = els.getOrNull(1) as? ListForm
            ?: error("fn requires a signature list (fn-name & params)")

        val sigEls = sigForm.els
        val fnName = (sigEls.firstOrNull() as? SymbolForm)?.name
            ?: error("fn signature must start with a name")

        val params = sigEls.drop(1).map {
            (it as? SymbolForm)?.name ?: error("fn parameter must be a symbol")
        }

        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) error("fn requires a body")

        var fnAnalyser = Analyser(truffleEnv, globalEnv = globalEnv)
        for (param in params) {
            val (newAnalyser, _) = fnAnalyser.withLocal(param)
            fnAnalyser = newAnalyser
        }

        val bodyExpr = fnAnalyser.analyseBody(bodyForms, form.loc)

        return FnExpr(fnName, params, bodyExpr, form.loc)
    }

    private fun analyseCall(form: ListForm): ValueExpr {
        val els = form.els
        val fnExpr = analyseValueExpr(els[0])

        // Check for macro expansion
        if (fnExpr is GlobalVarExpr) {
            val value = fnExpr.globalVar.value
            if (value is BridjeMacro) {
                if (expansionDepth >= MAX_EXPANSION_DEPTH) {
                    error("Maximum macro expansion depth ($MAX_EXPANSION_DEPTH) exceeded")
                }
                val interop = InteropLibrary.getUncached()
                val args = els.drop(1).toTypedArray<Any>()
                val expanded = interop.execute(value.fn, *args) as Form
                return Analyser(truffleEnv, globalEnv, locals, nextSlot, expansionDepth + 1)
                    .analyseValueExpr(expanded)
            }
        }

        val argExprs = els.drop(1).map { analyseValueExpr(it) }
        return CallExpr(fnExpr, argExprs, form.loc)
    }

    fun analyseTopLevel(form: Form): TopLevelDoOrExpr {
        if (form is ListForm) {
            val first = form.els.firstOrNull()
            if (first is SymbolForm && first.name == "do") {
                return TopLevelDo(form.els.drop(1))
            }
        }
        return TopLevelExpr(analyseExpr(form))
    }

    private fun tryHostLookup(name: String, loc: SourceSection?): TruffleObject? {
        val className = name.replace(':', '.')
        return try {
            truffleEnv.lookupHostSymbol(className) as TruffleObject
        } catch (_: Exception) {
            null
        }
    }
}
