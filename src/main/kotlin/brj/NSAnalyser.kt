package brj

private val NS = Symbol.intern("ns")
private val REFERS = Symbol.intern("refers")
private val ALIASES = Symbol.intern("aliases")

private fun refersAnalyser(it: AnalyserState): Map<Symbol, QSymbol> {
    val refers = mutableMapOf<Symbol, QSymbol>()

    it.varargs {
        val ns = it.expectForm<SymbolForm>().sym
        it.nested(SetForm::forms) {
            it.varargs {
                val sym = it.expectForm<SymbolForm>().sym
                refers[sym] = QSymbol.intern(ns, sym)
            }
        }
    }

    return refers
}

private fun aliasesAnalyser(it: AnalyserState): Map<Symbol, Symbol> {
    val aliases = mutableMapOf<Symbol, Symbol>()

    it.varargs {
        aliases[it.expectForm<SymbolForm>().sym] = it.expectForm<SymbolForm>().sym
    }

    return aliases
}

internal fun nsAnalyser(it: AnalyserState): NSEnv =
    it.nested(ListForm::forms) {
        it.expectSym(NS)
        var nsEnv = NSEnv(it.expectForm<SymbolForm>().sym)

        it.maybe {
            it.nested(RecordForm::forms) {
                it.varargs {
                    when (it.expectForm<SymbolForm>().sym) {
                        REFERS -> {
                            nsEnv = nsEnv.copy(refers = it.nested(RecordForm::forms, ::refersAnalyser))
                        }
                        ALIASES -> {
                            nsEnv = nsEnv.copy(aliases = it.nested(RecordForm::forms, ::aliasesAnalyser))
                        }
                        else -> TODO()
                    }
                }
            }

        }

        nsEnv
    }
