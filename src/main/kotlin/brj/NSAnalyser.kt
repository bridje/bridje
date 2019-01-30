package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym

private val NS = mkSym("ns")

private val REFERS = mkSym(":refers")
private val ALIASES = mkSym(":aliases")
private val IMPORTS = mkSym(":imports")
private val JAVA = mkSym("java")

internal class NSAnalyser(val ns: Symbol) {
    fun refersAnalyser(it: AnalyserState): Map<Symbol, QSymbol> {
        val refers = mutableMapOf<Symbol, QSymbol>()

        it.varargs {
            val nsSym = it.expectForm<SymbolForm>().sym
            it.nested(SetForm::forms) {
                it.varargs {
                    val sym = it.expectForm<SymbolForm>().sym
                    refers[sym] = mkQSym("${if (sym.isKeyword) ":" else ""}${nsSym.baseStr}/${sym.baseStr}")
                }
            }
        }

        return refers
    }

    fun aliasesAnalyser(it: AnalyserState): Map<Symbol, Symbol> {
        val aliases = mutableMapOf<Symbol, Symbol>()

        it.varargs {
            aliases[it.expectForm<SymbolForm>().sym] = it.expectForm<SymbolForm>().sym
        }

        return aliases
    }

    fun javaImportsAnalyser(it: AnalyserState): Map<QSymbol, JavaImport> {
        val javaImports = mutableMapOf<QSymbol, JavaImport>()

        it.varargs {
            val alias = it.expectForm<SymbolForm>().sym

            it.nested(ListForm::forms) {
                it.expectSym(JAVA)
                val clazz = Class.forName(it.expectForm<SymbolForm>().sym.baseStr)

                it.varargs {
                    it.nested(ListForm::forms) {
                        it.expectSym(TYPE_DEF)
                        val (sym, type) = ActionExprAnalyser(Env(), NSEnv(ns)).typeDefAnalyser(it)

                        val importSym = QSymbol.mkQSym("$alias/$sym")
                        javaImports[importSym] = JavaImport(clazz, importSym, type)
                    }
                }
            }
        }

        return javaImports
    }
}

internal fun nsAnalyser(it: AnalyserState, ns: Symbol): NSEnv =
    it.nested(ListForm::forms) {
        it.expectSym(NS)
        var nsEnv = NSEnv(it.expectSym(ns))
        val ana = NSAnalyser(nsEnv.ns)

        if (it.forms.isNotEmpty()) {
            it.nested(RecordForm::forms) {
                it.varargs {
                    val sym = it.expectForm<SymbolForm>().sym
                    nsEnv = when (sym) {
                        REFERS -> {
                            nsEnv.copy(refers = it.nested(RecordForm::forms, ana::refersAnalyser))
                        }

                        ALIASES -> {
                            nsEnv.copy(aliases = it.nested(RecordForm::forms, ana::aliasesAnalyser))
                        }

                        IMPORTS -> {
                            nsEnv.copy(javaImports = it.nested(RecordForm::forms, ana::javaImportsAnalyser))
                        }

                        else -> TODO()
                    }
                }
            }

        }

        nsEnv
    }
