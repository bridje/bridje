package brj

import brj.Form.ListForm
import brj.Form.SymbolForm
import brj.Types.MonoType.*
import brj.Types.Typing
import brj.ValueExpr.FnExpr
import brj.ValueExpr.ValueExprAnalyser

@Suppress("NestedLambdaShadowedImplicitParameter")
class ActionExprAnalyser(val brjEnv: BrjEnv, val nsEnv: BrjEnv.NSEnv) {
    data class DefExpr(val sym: Symbol, val expr: ValueExpr, val typing: Typing)

    data class TypeDefExpr(val sym: Symbol, val typing: Typing)

    val defAnalyser: FormsAnalyser<DefExpr> = {
        val form = it.expectForm<Form>()

        val (sym, paramSyms) = when (form) {
            is SymbolForm -> Pair(form.sym, null)
            is ListForm -> {
                it.nested(form.forms) {
                    Pair(
                        it.expectForm<Form.SymbolForm>().sym,
                        it.varargs { it.expectForm<Form.SymbolForm>().sym })
                }
            }

            else -> TODO()
        }

        val locals = paramSyms?.map { it to LocalVar(it) } ?: emptyList()

        val bodyExpr = ValueExprAnalyser(brjEnv, nsEnv, locals = locals.toMap()).analyseValueExpr(it.forms)

        val expr =
            if (paramSyms == null)
                bodyExpr
            else
                FnExpr(sym, locals.map(Pair<Symbol, LocalVar>::second), bodyExpr)

        DefExpr(sym, expr, Types.TypeChecker(brjEnv).valueExprTyping(expr))
    }

    private fun monoTypeAnalyser(it: Analyser.AnalyserState): Types.MonoType {
        val form = it.expectForm<Form>()
        return when (form) {
            is Form.SymbolForm -> {
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

            is Form.VectorForm ->
                VectorType(it.nested(form.forms) {
                    monoTypeAnalyser(it).also { _ -> it.expectEnd() }
                })

            is Form.SetForm ->
                SetType(it.nested(form.forms) {
                    monoTypeAnalyser(it).also { _ -> it.expectEnd() }
                })

            else -> TODO()
        }
    }

    val typeDefAnalyser: FormsAnalyser<TypeDefExpr> = {
        val form = it.expectForm<Form>()

        val (sym, params) = when (form) {
            is Form.ListForm -> {
                it.nested(form.forms) {
                    Pair(it.expectForm<Form.SymbolForm>().sym, it.varargs(::monoTypeAnalyser))
                }
            }

            is Form.SymbolForm -> {
                Pair(form.sym, null)
            }

            else -> TODO()
        }

        val returnType = monoTypeAnalyser(it)

        it.expectEnd()

        TypeDefExpr(sym, Typing(if (params != null) FnType(params, returnType) else returnType))
    }

    companion object {
        private val STR = Symbol.create("Str")
        private val BOOL = Symbol.create("Bool")
        private val INT = Symbol.create("Int")
        private val FLOAT = Symbol.create("Float")
        private val BIG_INT = Symbol.create("BigInt")
        private val BIG_FLOAT = Symbol.create("BigFloat")
    }
}