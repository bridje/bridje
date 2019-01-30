package brj

internal class TypeAnalyser(val env: Env, val nsEnv: NSEnv) {
    companion object {
        private val STR = Symbol.mkSym("Str")
        private val BOOL = Symbol.mkSym("Bool")
        private val INT = Symbol.mkSym("Int")
        private val FLOAT = Symbol.mkSym("Float")
        private val BIG_INT = Symbol.mkSym("BigInt")
        private val BIG_FLOAT = Symbol.mkSym("BigFloat")
    }

    val tvMapping: MutableMap<Symbol, TypeVarType> = mutableMapOf()

    private fun tv(sym: Symbol): TypeVarType? =
        if (Character.isLowerCase(sym.baseStr.first())) {
            tvMapping.getOrPut(sym) { TypeVarType() }
        } else null

    fun monoTypeAnalyser(it: AnalyserState): MonoType {
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

                    else -> {
                        TODO()
                    }
                }
            }

            is VectorForm -> VectorType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is SetForm -> SetType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is ListForm -> it.nested(form.forms) { AppliedType(monoTypeAnalyser(it), it.varargs(::monoTypeAnalyser)) }

            else -> TODO()
        }
    }

    fun tvAnalyser(it: AnalyserState): TypeVarType =
        tv(it.expectForm<SymbolForm>().sym) ?: TODO()
}