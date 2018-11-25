package brj

internal val DO = Symbol.intern("do")
internal val DEF = Symbol.intern("def")
internal val TYPE_DEF = Symbol.intern("::")
internal val DEF_DATA = Symbol.intern("defdata")

internal class ActionExprAnalyser(val env: Env, val nsEnv: NSEnv) {
    data class DefExpr(val sym: Symbol, val expr: ValueExpr, val type: Type)
    data class TypeDefExpr(val sym: Symbol, val type: Type)

    data class DefDataExpr(val sym: Symbol, val typeParams: List<TypeVarType>?, val constructors: List<DefDataConstructor> = emptyList()) {
        data class DefDataConstructor(val sym: Symbol, val params: List<MonoType>?)
    }

    fun defAnalyser(it: AnalyserState): DefExpr {
        val form = it.expectForm<Form>()

        val (sym, paramSyms) = when (form) {
            is SymbolForm -> Pair(form.sym, null)
            is ListForm -> {
                it.nested(form.forms) {
                    Pair(
                        it.expectForm<SymbolForm>().sym,
                        it.varargs { it.expectForm<SymbolForm>().sym })
                }
            }

            else -> TODO()
        }

        val locals = paramSyms?.map { it to LocalVar(it) } ?: emptyList()

        val bodyExpr = ValueExprAnalyser(env, nsEnv, locals = locals.toMap()).analyseValueExpr(it.forms)

        val expr =
            if (paramSyms == null)
                bodyExpr
            else
                FnExpr(sym, locals.map(Pair<Symbol, LocalVar>::second), bodyExpr)

        return DefExpr(sym, expr, valueExprType(env, expr))
    }

    fun typeDefAnalyser(it: AnalyserState): TypeDefExpr {
        val typeAnalyser = TypeAnalyser()

        val form = it.expectForm<Form>()
        val (sym, params) = when (form) {
            is ListForm -> {
                it.nested(form.forms) {
                    Pair(it.expectForm<SymbolForm>().sym, it.varargs(typeAnalyser::monoTypeAnalyser))
                }
            }

            is SymbolForm -> {
                Pair(form.sym, null)
            }

            else -> TODO()
        }

        val returnType = typeAnalyser.monoTypeAnalyser(it)

        it.expectEnd()

        return TypeDefExpr(sym, Type(if (params != null) FnType(params, returnType) else returnType))
    }

    fun defDataSigAnalyser(it: AnalyserState, typeAnalyser: TypeAnalyser = TypeAnalyser()): Pair<Symbol, List<TypeVarType>?> {
        val form = it.expectForm<Form>()

        return when (form) {
            is ListForm -> {
                it.nested(form.forms) {
                    Pair(it.expectForm<SymbolForm>().sym, it.varargs(typeAnalyser::tvAnalyser))
                }
            }

            is SymbolForm -> {
                Pair(form.sym, null)
            }

            else -> TODO()
        }
    }

    fun defDataAnalyser(it: AnalyserState): DefDataExpr {
        val typeAnalyser = TypeAnalyser()

        val (sym, typeParams) = defDataSigAnalyser(it, typeAnalyser)

        val constructors = it.varargs {
            val form = it.expectForm<Form>()

            when (form) {
                is ListForm -> {
                    it.nested(form.forms) {
                        DefDataExpr.DefDataConstructor(it.expectForm<SymbolForm>().sym, it.varargs(typeAnalyser::monoTypeAnalyser))
                    }
                }

                is SymbolForm -> {
                    DefDataExpr.DefDataConstructor(form.sym, null)
                }

                else -> TODO()
            }
        }

        return DefDataExpr(sym, typeParams, constructors)
    }


}