package brj

data class Env(val nses: Map<Symbol, NSEnv> = emptyMap()) {
    operator fun plus(newNsEnv: NSEnv): Env = Env(nses + (newNsEnv.ns to newNsEnv))
}

data class NSEnv(val ns: Symbol,
                 val refers: Map<LocalIdent, GlobalIdent> = emptyMap(),
                 val aliases: Map<Symbol, Symbol> = emptyMap(),
                 val dataTypes: Map<Symbol, DataType> = emptyMap(),
                 val vars: Map<LocalIdent, GlobalVar> = emptyMap()) {

    data class GlobalVar(val sym: LocalIdent, val typing: Types.Typing, val value: Any?)

    data class DataType(val sym: QSymbol, val typeVars: List<Types.MonoType.TypeVarType>?, val constructors: List<Keyword>)

    data class DataTypeConstructor(val kw: Keyword, val dataType: DataType, val paramTypes: List<Types.MonoType>?, val value: Any?)

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

        private val refersAnalyser: FormsAnalyser<Map<LocalIdent, GlobalIdent>> = {
            val refers = mutableMapOf<LocalIdent, GlobalIdent>()

            it.varargs {
                val ns = it.expectForm<SymbolForm>().sym
                it.nested(SetForm::forms) {
                    it.varargs {
                        val sym = it.expectForm<SymbolForm>().sym
                        refers[sym] = QSymbol.intern(ns, sym)
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
                var nsEnv = NSEnv(it.expectForm<SymbolForm>().sym)

                it.maybe {
                    it.nested(RecordForm::forms) {
                        it.varargs {
                            val kw = it.expectForm<KeywordForm>()
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