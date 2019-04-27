package brj.analyser

import brj.*
import brj.SymbolKind.*
import brj.types.*

internal interface ITypeVarFactory {
    fun mkTypeVar(sym: Symbol): TypeVarType

    class TypeVarFactory : ITypeVarFactory {
        val tvMapping: MutableMap<Symbol, TypeVarType> = mutableMapOf()

        override fun mkTypeVar(sym: Symbol) = tvMapping.getOrPut(sym) { TypeVarType() }
    }
}

internal class TypeAnalyser(val env: Env, val nsEnv: NSEnv,
                            private val instantiator: Instantiator = Instantiator(),
                            private val typeVarFactory: ITypeVarFactory = ITypeVarFactory.TypeVarFactory()) {

    fun typeVarAnalyser(it: ParserState) = typeVarFactory.mkTypeVar(it.expectSym(VAR_SYM))

    fun monoTypeAnalyser(it: ParserState): MonoType {
        val form = it.expectForm<Form>()

        return when (form) {
            is SymbolForm -> {
                when (form.sym) {
                    STR -> StringType
                    BOOL -> BoolType
                    INT -> IntType
                    FLOAT -> FloatType
                    BIG_INT -> BigIntType
                    BIG_FLOAT -> BigFloatType
                    SYMBOL -> SymbolType
                    QSYMBOL -> QSymbolType

                    else -> when (form.sym.symbolKind) {
                        VAR_SYM -> typeVarFactory.mkTypeVar(form.sym)
                        TYPE_ALIAS_SYM -> {
                            // TODO kind check
                            resolveTypeAlias(env, nsEnv, form.sym)?.let { TypeAliasType(it, emptyList()) } ?: TODO()
                        }
                        else -> TODO()
                    }
                }
            }

            is QSymbolForm -> {
                when (form.sym.symbolKind) {
                    TYPE_ALIAS_SYM -> {
                        // TODO kind check
                        resolveTypeAlias(env, nsEnv, form.sym)?.let { TypeAliasType(it, emptyList()) } ?: TODO()
                    }
                    else -> TODO()
                }
            }

            is VectorForm -> VectorType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is SetForm -> SetType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is ListForm -> it.nested(form.forms) {
                it.or({
                    it.maybe { it.expectSym(FN_TYPE) }?.let { _ ->
                        val types = it.varargs { monoTypeAnalyser(it) }
                        FnType(types.dropLast(1), types.last())
                    }
                }, {
                    it.maybe { it.expectSym(VARIANT_TYPE) }?.let { _ ->
                        VariantType(
                            possibleKeys = it.varargs {
                                data class Preamble(val variantSym: Ident, val typeParams: List<MonoType>)

                                val preamble = it.or({
                                    it.maybe { it.expectIdent(VARIANT_KEY_SYM) }?.let { Preamble(it, emptyList()) }
                                }, {
                                    it.maybe { it.expectForm<ListForm>() }?.let { lf -> it.nested(lf.forms) { Preamble(it.expectIdent(VARIANT_KEY_SYM), it.varargs { monoTypeAnalyser(it) }) } }
                                }) ?: TODO()

                                val variantKey = (resolve(env, nsEnv, preamble.variantSym) as? VariantKeyVar)?.variantKey
                                    ?: TODO()

                                // TODO check kind
                                variantKey to RowKey(preamble.typeParams)
                            }.toMap(),
                            typeVar = RowTypeVar(true))

                    }
                }, {
                    it.maybe { it.expectSym(TYPE_ALIAS_SYM) }?.let { sym ->
                        // TODO kind check
                        TypeAliasType(resolveTypeAlias(env, nsEnv, sym) ?: TODO(), it.varargs(this::monoTypeAnalyser))
                    }
                }) ?: TODO()
            }

            else -> TODO()
        }
    }
}