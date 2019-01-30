package brj

internal val DO = Symbol.mkSym("do")
internal val DEF = Symbol.mkSym("def")
internal val TYPE_DEF = Symbol.mkSym("::")

data class DefExpr(val sym: Symbol, val expr: ValueExpr, val type: Type)
data class TypeDefExpr(val sym: Symbol, val type: Type)

internal data class ActionExprAnalyser(val env: Env, val nsEnv: NSEnv, private val typeDefs: Map<Symbol, Type> = emptyMap()) {

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


        val actualType = valueExprType(expr)

        val expectedType = nsEnv.vars[sym]?.type ?: typeDefs[sym]

        if (expectedType != null && !(actualType.matches(expectedType))) {
            TODO()
        }

        return DefExpr(sym, expr, (expectedType ?: actualType).copy(effects = actualType.effects))
    }

    fun typeDefAnalyser(it: AnalyserState): TypeDefExpr {
        val typeAnalyser = TypeAnalyser(env, nsEnv)

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

        return TypeDefExpr(sym, Type(if (params != null) FnType(params, returnType) else returnType, emptySet()))
    }
}