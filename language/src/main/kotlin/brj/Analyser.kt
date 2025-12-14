package brj

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.source.SourceSection
import java.util.concurrent.atomic.AtomicInteger

class Analyser(
    private val truffleEnv: TruffleLanguage.Env,
    private val globalEnv: GlobalEnv = GlobalEnv(),
    private val locals: Map<String, LocalVar> = emptyMap(),
    private val nextSlot: AtomicInteger = AtomicInteger(0)
) {
    val slotCount: Int get() = nextSlot.get()

    internal fun withLocal(name: String): Pair<Analyser, LocalVar> {
        val lv = LocalVar(name, nextSlot.getAndIncrement())
        return Analyser(truffleEnv, globalEnv, locals + (name to lv), nextSlot) to lv
    }

    private fun analyseListForm(form: ListForm): Expr {
        val els = form.els
        val first = els.firstOrNull() ?: TODO("empty list not supported yet")

        return when (first) {
            is SymbolForm -> when (first.name) {
                "let" -> analyseLet(form)
                "fn" -> analyseFn(form)
                "do" -> analyseDo(form)
                "if" -> analyseIf(form)
                "def" -> analyseDef(form)
                "deftag" -> analyseDefTag(form)
                "case" -> analyseCase(form)
                else -> analyseCall(form)
            }

            else -> analyseCall(form)
        }
    }

    private fun analyseDo(form: ListForm): Expr {
        val bodyForms = form.els.drop(1)
        if (bodyForms.isEmpty()) error("do requires at least one expression")
        return analyseBody(bodyForms, form.loc)
    }

    private fun analyseIf(form: ListForm): IfExpr {
        val els = form.els
        // (if pred then else)
        if (els.size != 4) error("if requires exactly 3 arguments: predicate, then, else")
        val predExpr = analyseForm(els[1])
        val thenExpr = analyseForm(els[2])
        val elseExpr = analyseForm(els[3])
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
                val valueExpr = analyseForm(els.getOrNull(2) ?: error("def requires a value"))
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

    private fun analyseCase(form: ListForm): CaseExpr {
        val els = form.els
        if (els.size < 3) error("case requires a scrutinee and at least one branch")

        val scrutinee = analyseForm(els[1])
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
                val bodyExpr = analyseForm(patternForm)
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
                    val bodyExpr = analyseForm(bodyForm)
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

                val bodyExpr = branchAnalyser.analyseForm(bodyForm)
                CaseBranch(TagPattern(tagValue, bindings, patternForm.loc), bodyExpr, patternForm.loc)
            }
            else -> error("case pattern must be a tag")
        }
    }

    internal fun analyseBody(forms: List<Form>, loc: com.oracle.truffle.api.source.SourceSection?): Expr {
        return when {
            forms.isEmpty() -> error("body requires at least one expression")
            forms.size == 1 -> analyseForm(forms[0])
            else -> {
                val sideEffects = forms.dropLast(1).map { analyseForm(it) }
                val result = analyseForm(forms.last())
                DoExpr(sideEffects, result, loc)
            }
        }
    }

    private fun analyseLet(form: ListForm): Expr {
        val els = form.els
        // (let [bindings...] body...)
        // els[0] = 'let', els[1] = bindings vector, els[2..] = body

        val bindingsForm = els.getOrNull(1) as? VectorForm
            ?: error("let requires a vector of bindings")

        val bindingEls = bindingsForm.els
        if (bindingEls.size % 2 != 0) {
            error("let bindings must have even number of forms")
        }

        // Body is everything after the bindings vector
        val bodyForms = els.drop(2)
        if (bodyForms.isEmpty()) {
            error("let requires a body")
        }

        // Process bindings, building nested LetExprs
        return analyseBindings(bindingEls, bodyForms, form.loc)
    }

    private fun analyseBindings(
        bindingEls: List<Form>,
        bodyForms: List<Form>,
        loc: com.oracle.truffle.api.source.SourceSection?
    ): Expr {
        if (bindingEls.isEmpty()) {
            return analyseBody(bodyForms, loc)
        }

        val nameForm = bindingEls[0] as? SymbolForm
            ?: error("binding name must be a symbol")
        val valueForm = bindingEls[1]

        val bindingExpr = analyseForm(valueForm)
        val (newAnalyser, localVar) = withLocal(nameForm.name)

        val bodyExpr = newAnalyser.analyseBindings(bindingEls.drop(2), bodyForms, loc)

        return LetExpr(localVar, bindingExpr, bodyExpr, loc)
    }

    private fun analyseFn(form: ListForm): FnExpr {
        val els = form.els
        // (fn (fn-name & params) & body)

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

        // Create new analyser with fresh slot counter for fn body, preserving globalEnv
        var fnAnalyser = Analyser(truffleEnv, globalEnv = globalEnv)
        for (param in params) {
            val (newAnalyser, _) = fnAnalyser.withLocal(param)
            fnAnalyser = newAnalyser
        }

        val bodyExpr = fnAnalyser.analyseBody(bodyForms, form.loc)

        return FnExpr(fnName, params, bodyExpr, form.loc)
    }

    private fun analyseCall(form: ListForm): CallExpr {
        val els = form.els
        val fnExpr = analyseForm(els[0])
        val argExprs = els.drop(1).map { analyseForm(it) }
        return CallExpr(fnExpr, argExprs, form.loc)
    }

    fun analyseTopLevel(form: Form): TopLevelDoOrExpr {
        if (form is ListForm) {
            val first = form.els.firstOrNull()
            if (first is SymbolForm) {
                when (first.name) {
                    "do" -> return TopLevelDo(form.els.drop(1))
                    "def" -> return TopLevelExpr(analyseDef(form))
                    "deftag" -> return TopLevelExpr(analyseDefTag(form))
                }
            }
        }
        return TopLevelExpr(analyseForm(form))
    }

    private fun tryHostLookup(name: String, loc: SourceSection?): TruffleObjectExpr? {
        val className = name.replace(':', '.')
        return try {
            val hostClass = truffleEnv.lookupHostSymbol(className) as TruffleObject
            TruffleObjectExpr(hostClass, loc)
        } catch (_: Exception) {
            null
        }
    }

    fun analyseForm(form: Form): Expr {
        return when (form) {
            is IntForm -> IntExpr(form.value, form.loc)
            is DoubleForm -> DoubleExpr(form.value, form.loc)
            is BigIntForm -> BigIntExpr(form.value, form.loc)
            is BigDecForm -> BigDecExpr(form.value, form.loc)
            is StringForm -> StringExpr(form.value, form.loc)
            is SymbolForm -> when (form.name) {
                "true" -> BoolExpr(true, form.loc)
                "false" -> BoolExpr(false, form.loc)
                else -> locals[form.name]?.let { LocalVarExpr(it, form.loc) }
                    ?: globalEnv[form.name]?.let { GlobalVarExpr(it, form.loc) }
                    ?: tryHostLookup(form.name, form.loc)
                    ?: error("Unknown symbol: ${form.name}")
            }
            is KeywordForm -> TODO("keyword form")

            is QualifiedSymbolForm -> {
                val className = form.namespace.replace(':', '.')
                val hostClass = try {
                    truffleEnv.lookupHostSymbol(className) as TruffleObject
                } catch (_: Exception) {
                    error("Unknown namespace: ${form.namespace}")
                }
                if (form.member == "new") {
                    HostConstructorExpr(hostClass, form.loc)
                } else {
                    HostStaticMethodExpr(hostClass, form.member, form.loc)
                }
            }

            is ListForm -> analyseListForm(form)
            is VectorForm -> VectorExpr(form.els.map { analyseForm(it) }, form.loc)
            is SetForm -> SetExpr(form.els.map { analyseForm(it) }, form.loc)
            is MapForm -> MapExpr(form.els.map { analyseForm(it) }, form.loc)
        }
    }
}
