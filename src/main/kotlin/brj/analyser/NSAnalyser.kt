package brj.analyser

import brj.*
import brj.Symbol.Companion.mkSym
import brj.SymbolKind.TYPE_ALIAS_SYM
import brj.SymbolKind.VAR_SYM

internal val NS = mkSym("ns")
internal val REFERS = mkSym(":refers")
internal val ALIASES = mkSym(":aliases")
internal val JAVA = mkSym("java")

internal fun parseNSSym(forms: List<Form>): Symbol? {
    return ParserState(forms).maybe { it.nested(ListForm::forms) { it.expectSym(NS); it.expectSym(VAR_SYM) } }
}

internal class NSAnalyser(val ns: Symbol) {
    fun refersAnalyser(it: ParserState): Map<Symbol, QSymbol> {
        val refers = mutableMapOf<Symbol, QSymbol>()

        it.varargs {
            val nsSym = it.expectForm<SymbolForm>().sym
            it.nested(SetForm::forms) {
                it.varargs {
                    val sym = it.expectForm<SymbolForm>().sym
                    refers[sym] = QSymbol.mkQSym(nsSym, sym)
                }
            }
        }

        return refers
    }

    fun aliasesAnalyser(it: ParserState): Map<Symbol, Alias> {
        return it.varargs {
            it.or({
                it.maybe { it.expectSym(VAR_SYM) }?.let { sym -> sym to BridjeAlias(it.expectSym(VAR_SYM)) }
            }, {
                it.maybe { it.expectSym(TYPE_ALIAS_SYM) }?.let { sym ->
                    sym to JavaAlias(mkSym("$ns\$$sym"), it.nested(ListForm::forms) {
                        it.expectSym(JAVA)
                        Class.forName(it.expectSym(VAR_SYM).baseStr).also { _ -> it.expectEnd() }
                    })
                }
            }) ?: TODO()
        }.toMap()
    }

    internal fun analyseNS(form: Form): NSEnv =
        ParserState(listOf(form)).nested(ListForm::forms) {
            it.expectSym(NS)
            var nsEnv = NSEnv(it.expectSym(VAR_SYM))

            if (it.forms.isNotEmpty()) {
                it.nested(RecordForm::forms) {
                    it.varargs {
                        val sym = it.expectForm<SymbolForm>().sym
                        nsEnv = when (sym) {
                            REFERS -> {
                                nsEnv.copy(refers = it.nested(RecordForm::forms, ::refersAnalyser))
                            }

                            ALIASES -> {
                                nsEnv.copy(aliases = it.nested(RecordForm::forms, ::aliasesAnalyser))
                            }

                            else -> TODO()
                        }
                    }
                }
            }

            it.expectEnd()
            nsEnv
        }
}

