@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj.analyser

import brj.*
import brj.Symbol.Companion.mkSym
import brj.SymbolKind.TYPE_ALIAS_SYM
import brj.SymbolKind.VAR_SYM

internal data class NSHeader(val ns: Symbol,
                             val refers: Map<Symbol, QSymbol> = emptyMap(),
                             val aliases: Map<Symbol, Alias> = emptyMap()) {

    val deps by lazy {
        refers.values.mapTo(mutableSetOf()) { it.ns } +
            aliases.values.mapNotNull {
                when (it) {
                    is BridjeAlias -> it.ns
                    is JavaAlias -> null
                }
            }
    }

    companion object {
        private val NS = mkSym("ns")
        private val REFERS = mkSym(":refers")
        private val ALIASES = mkSym(":aliases")
        private val JAVA = mkSym("java")

        private fun refersAnalyser(it: ParserState): Map<Symbol, QSymbol> {
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

        private fun aliasesAnalyser(it: ParserState, ns: Symbol): Map<Symbol, Alias> {
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

        internal fun nsHeaderParser(it: ParserState): NSHeader? =
            it.maybe {
                it.nested(ListForm::forms) {
                    it.expectSym(NS)
                    it
                }
            }?.let {
                it.nested(ListForm::forms) {
                    it.expectSym(NS)
                    val ns = it.expectSym(VAR_SYM)
                    var nsHeader = NSHeader(ns)

                    if (it.forms.isNotEmpty()) {
                        it.nested(RecordForm::forms) {
                            it.varargs {
                                val sym = it.expectForm<SymbolForm>().sym
                                nsHeader = when (sym) {
                                    REFERS -> {
                                        nsHeader.copy(refers = it.nested(RecordForm::forms, ::refersAnalyser))
                                    }

                                    ALIASES -> {
                                        nsHeader.copy(aliases = it.nested(RecordForm::forms) { aliasesAnalyser(it, ns) })
                                    }

                                    else -> TODO()
                                }
                            }
                        }
                    }

                    it.expectEnd()
                    nsHeader
                }
            }
    }
}

