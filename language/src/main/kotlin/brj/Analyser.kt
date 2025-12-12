package brj

import java.util.concurrent.atomic.AtomicInteger

class Analyser(
    private val locals: Map<String, LocalVar> = emptyMap(),
    private val nextSlot: AtomicInteger = AtomicInteger(0)
) {
    val slotCount: Int get() = nextSlot.get()

    internal fun withLocal(name: String): Pair<Analyser, LocalVar> {
        val lv = LocalVar(name, nextSlot.getAndIncrement())
        return Analyser(locals + (name to lv), nextSlot) to lv
    }

    private fun analyseListForm(form: ListForm): Expr {
        val els = form.els
        val first = els.firstOrNull() ?: TODO("empty list not supported yet")

        return when (first) {
            is SymbolForm -> when (first.name) {
                "let" -> analyseLet(form)
                "fn" -> analyseFn(form)
                "def" -> TODO("def")
                "if" -> TODO("if")
                else -> analyseCall(form)
            }
            else -> analyseCall(form)
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

        // For now, we'll just use the last body form as the result
        // (implicit do would wrap multiple forms, but we'll simplify for now)
        val bodyForm = if (bodyForms.size == 1) bodyForms[0] else TODO("implicit do not yet supported")

        // Process bindings, building nested LetExprs
        return analyseBindings(bindingEls, bodyForm, form.loc)
    }

    private fun analyseBindings(
        bindingEls: List<Form>,
        bodyForm: Form,
        loc: com.oracle.truffle.api.source.SourceSection?
    ): Expr {
        if (bindingEls.isEmpty()) {
            return analyseForm(bodyForm)
        }

        val nameForm = bindingEls[0] as? SymbolForm
            ?: error("binding name must be a symbol")
        val valueForm = bindingEls[1]

        val bindingExpr = analyseForm(valueForm)
        val (newAnalyser, localVar) = withLocal(nameForm.name)

        val bodyExpr = newAnalyser.analyseBindings(bindingEls.drop(2), bodyForm, loc)

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

        // For now, just use single body form (implicit do will come later)
        val bodyForm = if (bodyForms.size == 1) bodyForms[0] else TODO("implicit do not yet supported")

        // Create new analyser with fresh slot counter for fn body
        var fnAnalyser = Analyser()
        for (param in params) {
            val (newAnalyser, _) = fnAnalyser.withLocal(param)
            fnAnalyser = newAnalyser
        }

        val bodyExpr = fnAnalyser.analyseForm(bodyForm)

        return FnExpr(fnName, params, bodyExpr, form.loc)
    }

    private fun analyseCall(form: ListForm): CallExpr {
        val els = form.els
        val fnExpr = analyseForm(els[0])
        val argExprs = els.drop(1).map { analyseForm(it) }
        return CallExpr(fnExpr, argExprs, form.loc)
    }

    fun analyseForm(form: Form): Expr {
        return when (form) {
            is IntForm -> IntExpr(form.value, form.loc)
            is DoubleForm -> DoubleExpr(form.value, form.loc)
            is BigIntForm -> BigIntExpr(form.value, form.loc)
            is BigDecForm -> BigDecExpr(form.value, form.loc)
            is StringForm -> StringExpr(form.value, form.loc)
            is SymbolForm -> locals[form.name]?.let { LocalVarExpr(it, form.loc) }
                ?: error("Unknown local: ${form.name}")
            is KeywordForm -> TODO("keyword form")

            is ListForm -> analyseListForm(form)
            is VectorForm -> VectorExpr(form.els.map { analyseForm(it) }, form.loc)
            is SetForm -> SetExpr(form.els.map { analyseForm(it) }, form.loc)
            is MapForm -> MapExpr(form.els.map { analyseForm(it) }, form.loc)
        }
    }
}
