package brj

internal fun dummyMacroEvaluator() = object : MacroEvaluator {
    override fun evalMacro(env: RuntimeEnv, macroVar: DefMacroVar, argForms: List<Form>): Form =
        ListForm(listOf(QSymbolForm(macroVar.sym)) + argForms)
}

