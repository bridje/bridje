package brj

import brj.BrjLanguage.Companion.TYPE_DEF

private val NS = Symbol.intern("ns")

private val REFERS = Symbol.intern("refers")
private val ALIASES = Symbol.intern("aliases")
private val IMPORTS = Symbol.intern("imports")
private val EXPORTS = Symbol.intern("exports")

internal class NSAnalyser(val ns: Symbol) {
    fun refersAnalyser(it: AnalyserState): Map<Symbol, QSymbol> {
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
            val clazz = Class.forName(it.expectForm<SymbolForm>().sym.nameStr)
            val classSym = Symbol.intern(clazz.simpleName)

            it.nested(SetForm::forms) {
                it.varargs {
                    it.nested(ListForm::forms) {
                        it.expectSym(TYPE_DEF)
                        val (sym, type) = ActionExprAnalyser(Env(), NSEnv(ns)).typeDefAnalyser(it)

                        val importSym = QSymbol.intern(classSym, sym)
                        javaImports[importSym] = JavaImport(clazz, importSym, type)
                    }
                }
            }
        }

        return javaImports
    }
}

internal fun nsAnalyser(it: AnalyserState): NSEnv =
    it.nested(ListForm::forms) {
        it.expectSym(NS)
        var nsEnv = NSEnv(it.expectForm<SymbolForm>().sym)
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
