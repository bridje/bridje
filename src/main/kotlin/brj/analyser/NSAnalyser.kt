package brj.analyser

import brj.*
import brj.Symbol.Companion.mkSym
import brj.SymbolKind.VAR_SYM

internal val NS = mkSym("ns")
internal val REFERS = mkSym(":refers")
internal val ALIASES = mkSym(":aliases")
internal val IMPORTS = mkSym(":imports")
internal val JAVA = mkSym("java")

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

    fun aliasesAnalyser(it: ParserState): Map<Symbol, Symbol> {
        val aliases = mutableMapOf<Symbol, Symbol>()

        it.varargs {
            aliases[it.expectForm<SymbolForm>().sym] = it.expectForm<SymbolForm>().sym
        }

        return aliases
    }

    fun javaImportsAnalyser(it: ParserState): Map<Symbol, JavaImport> {
        val javaImports = mutableMapOf<Symbol, JavaImport>()

        it.varargs {
            val alias = it.expectForm<SymbolForm>().sym

            it.nested(ListForm::forms) {
                it.expectSym(JAVA)
                val clazz = Class.forName(it.expectForm<SymbolForm>().sym.baseStr)

                it.varargs {
                    it.nested(ListForm::forms) {
                        it.expectSym(DECL)

                        val varDeclExpr = (ExprAnalyser(Env(), NSEnv(ns), BrjLanguage.BrjEmitter).analyseDecl(it)) as? VarDeclExpr
                            ?: TODO()

                        if (varDeclExpr.type.effects.isNotEmpty()) TODO()

                        val name = varDeclExpr.sym.base.baseStr
                        val sym = mkSym("$alias.$name")
                        javaImports[sym] = JavaImport(QSymbol.mkQSym(ns, sym), clazz, name, varDeclExpr.type)
                    }
                }
            }
        }

        return javaImports
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

                            IMPORTS -> {
                                nsEnv.copy(javaImports = it.nested(RecordForm::forms, ::javaImportsAnalyser))
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

