package brj

internal class TypeAnalyser {
    companion object {
        private val STR = Symbol.intern("Str")
        private val BOOL = Symbol.intern("Bool")
        private val INT = Symbol.intern("Int")
        private val FLOAT = Symbol.intern("Float")
        private val BIG_INT = Symbol.intern("BigInt")
        private val BIG_FLOAT = Symbol.intern("BigFloat")
    }

    val tvMapping: MutableMap<Symbol, Types.MonoType.TypeVarType> = mutableMapOf()

    private fun tv(sym: Symbol): Types.MonoType.TypeVarType? =
        if (Character.isLowerCase(sym.nameStr.first())) {
            tvMapping.getOrPut(sym) { Types.MonoType.TypeVarType() }
        } else null

    fun monoTypeAnalyser(it: AnalyserState): Types.MonoType {
        val form = it.expectForm<Form>()
        return when (form) {
            is SymbolForm -> {
                when (form.sym) {
                    STR -> Types.MonoType.StringType
                    BOOL -> Types.MonoType.BoolType
                    INT -> Types.MonoType.IntType
                    FLOAT -> Types.MonoType.FloatType
                    BIG_INT -> Types.MonoType.BigIntType
                    BIG_FLOAT -> Types.MonoType.BigFloatType

                    else -> {
                        tv(form.sym) ?: TODO()
                    }
                }
            }

            is VectorForm -> Types.MonoType.VectorType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is SetForm -> Types.MonoType.SetType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is ListForm -> it.nested(form.forms) { Types.MonoType.AppliedType(monoTypeAnalyser(it), it.varargs(::monoTypeAnalyser)) }

            else -> TODO()
        }
    }

    fun tvAnalyser(it: AnalyserState): Types.MonoType.TypeVarType =
        tv(it.expectForm<SymbolForm>().sym) ?: TODO()
}