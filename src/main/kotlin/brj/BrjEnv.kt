package brj

import brj.Form.*
import brj.Types.MonoType
import brj.Types.MonoType.TypeVarType

data class BrjEnv(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    data class NSEnv(val ns: Symbol,
                     val refers: Map<Symbol, NamespacedSymbol> = emptyMap(),
                     val aliases: Map<Symbol, Symbol> = emptyMap(),
                     val vars: Map<Symbol, GlobalVar> = emptyMap(),
                     val adts: Map<Symbol, ADT> = emptyMap()) {

        data class GlobalVar(val sym: Symbol, val value: Any?, val typing: Types.Typing)

        data class ADT(val sym: Symbol, val typeVars: List<TypeVarType>?, val constructors: List<ADTConstructor> = emptyList()) {
            data class ADTConstructor(val kw: Keyword, val params: List<MonoType>?)
        }

        operator fun plus(newGlobalVar: GlobalVar): NSEnv = copy(vars = vars + (newGlobalVar.sym to newGlobalVar))
        operator fun plus(newADT: ADT): NSEnv = copy(adts = adts + (newADT.sym to newADT))

        val deps: Set<Symbol> by lazy {
            aliases.values.toSet() + refers.values.map { it.ns }
        }

        @Suppress("NestedLambdaShadowedImplicitParameter")
        companion object {
            private val NS = Symbol.create("ns")

            private val refersAnalyser: FormsAnalyser<Map<Symbol, NamespacedSymbol>> = {
                val refers = mutableMapOf<Symbol, NamespacedSymbol>()

                it.varargs {
                    val ns = it.expectForm<SymbolForm>().sym
                    it.nested(SetForm::forms) {
                        it.varargs {
                            val sym = it.expectForm<SymbolForm>().sym
                            refers[sym] = NamespacedSymbol.create(ns, sym)
                        }
                    }
                }

                refers
            }

            private val aliasesAnalyser: FormsAnalyser<Map<Symbol, Symbol>> = {
                val aliases = mutableMapOf<Symbol, Symbol>()

                it.varargs {
                    aliases[it.expectForm<SymbolForm>().sym] = it.expectForm<SymbolForm>().sym
                }

                aliases
            }

            val nsAnalyser: FormsAnalyser<NSEnv> = {
                it.nested(ListForm::forms) {
                    it.expectSym(NS)
                    var nsEnv = NSEnv(it.expectForm<Form.SymbolForm>().sym)

                    it.maybe {
                        it.nested(RecordForm::forms) {
                            it.varargs {
                                val kw = it.expectForm<Form.KeywordForm>()
                                when (kw.kw.name) {
                                    "refers" -> {
                                        nsEnv = nsEnv.copy(refers = it.nested(RecordForm::forms, refersAnalyser))
                                    }
                                    "aliases" -> {
                                        nsEnv = nsEnv.copy(aliases = it.nested(RecordForm::forms, aliasesAnalyser))
                                    }
                                    else -> TODO()
                                }
                            }
                        }

                    }

                    nsEnv
                }
            }
        }
    }

    operator fun plus(newNsEnv: Pair<Symbol, NSEnv>): BrjEnv = BrjEnv(nses + newNsEnv)
}