package brj

import brj.AQSymbol.QSymbol
import brj.ASymbol.Keyword
import brj.ASymbol.Symbol
import brj.Form.*
import brj.Types.MonoType
import brj.Types.MonoType.TypeVarType
import brj.Types.Typing

data class BrjEnv(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    data class NSEnv(val ns: Symbol,
                     val refers: Map<ASymbol, AQSymbol> = emptyMap(),
                     val aliases: Map<Symbol, Symbol> = emptyMap(),
                     val dataTypes: Map<Symbol, DataType> = emptyMap(),
                     val vars: Map<ASymbol, GlobalVar> = emptyMap()
    ) {

        data class GlobalVar(val sym: ASymbol, val typing: Typing, val value: Any?)

        data class DataType(val sym: QSymbol, val typeVars: List<TypeVarType>?, val constructors: List<Keyword>)

        data class DataTypeConstructor(val kw: Keyword, val dataType: DataType, val paramTypes: List<MonoType>?, val value: Any?)

        operator fun plus(newGlobalVar: GlobalVar): NSEnv = copy(vars = vars + (newGlobalVar.sym to newGlobalVar))
        operator fun plus(newDataType: DataType): NSEnv = copy(dataTypes = dataTypes + (newDataType.sym.name to newDataType))

        val deps: Set<Symbol> by lazy {
            aliases.values.toSet() + refers.values.map { it.ns }
        }

        @Suppress("NestedLambdaShadowedImplicitParameter")
        companion object {
            private val NS = Symbol.intern("ns")
            private val REFERS = Symbol.intern("refers")
            private val ALIASES = Symbol.intern("aliases")

            private val refersAnalyser: FormsAnalyser<Map<ASymbol, AQSymbol>> = {
                val refers = mutableMapOf<ASymbol, AQSymbol>()

                it.varargs {
                    val ns = it.expectForm<ASymbolForm.SymbolForm>().sym
                    it.nested(SetForm::forms) {
                        it.varargs {
                            val sym = it.expectForm<ASymbolForm.SymbolForm>().sym
                            refers[sym] = QSymbol.intern(ns, sym)
                        }
                    }
                }

                refers
            }

            private val aliasesAnalyser: FormsAnalyser<Map<Symbol, Symbol>> = {
                val aliases = mutableMapOf<Symbol, Symbol>()

                it.varargs {
                    aliases[it.expectForm<ASymbolForm.SymbolForm>().sym] = it.expectForm<ASymbolForm.SymbolForm>().sym
                }

                aliases
            }

            val nsAnalyser: FormsAnalyser<NSEnv> = {
                it.nested(ListForm::forms) {
                    it.expectSym(NS)
                    var nsEnv = NSEnv(it.expectForm<ASymbolForm.SymbolForm>().sym)

                    it.maybe {
                        it.nested(RecordForm::forms) {
                            it.varargs {
                                val kw = it.expectForm<ASymbolForm.KeywordForm>()
                                when (kw.sym.name) {
                                    REFERS -> {
                                        nsEnv = nsEnv.copy(refers = it.nested(RecordForm::forms, refersAnalyser))
                                    }
                                    ALIASES -> {
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

    operator fun plus(newNsEnv: NSEnv): BrjEnv = BrjEnv(nses + (newNsEnv.ns to newNsEnv))
}