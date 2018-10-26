package brj

import brj.Form.*
import brj.Types.MonoType.*
import brj.Types.Typing

sealed class ActionExpr {
    data class DefExpr(val sym: Symbol, val params: List<LocalVar>?, val expr: ValueExpr) : ActionExpr()
    data class TypedefExpr(val sym: Symbol, val typing: Typing) : ActionExpr()

    @Suppress("NestedLambdaShadowedImplicitParameter")
    data class ActionExprAnalyser(val brjEnv: BrjEnv, val ns: Symbol) {
        private val defAnalyser: FormsAnalyser<ActionExpr> = {
            val (sym, paramSyms) = it.or({
                Pair(it.expectForm<Form.SymbolForm>().sym, null)
            }, {
                it.nested(ListForm::forms) {
                    Pair(
                        it.expectForm<Form.SymbolForm>().sym,
                        it.varargs { it.expectForm<Form.SymbolForm>().sym })
                }
            }) ?: throw Analyser.AnalyserError.InvalidDefDefinition

            val localMapping = paramSyms?.associate { it to LocalVar(it) } ?: emptyMap()

            val params = paramSyms?.map { localMapping[it]!! }

            val expr = ValueExpr.ValueExprAnalyser(brjEnv, ns, locals = localMapping).analyseValueExpr(it.forms)

            DefExpr(sym, params, expr)
        }

        private fun monoTypeAnalyser(it: Analyser.AnalyserState): Types.MonoType {
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

                is VectorForm ->
                    VectorType(it.nested(form.forms) {
                        monoTypeAnalyser(it).also { _ -> it.expectEnd() }
                    })

                is SetForm ->
                    SetType(it.nested(form.forms) {
                        monoTypeAnalyser(it).also { _ -> it.expectEnd() }
                    })

                else -> TODO()
            }
        }

        private val typedefAnalyser: FormsAnalyser<ActionExpr> = {
            val (sym, params) = it.or({
                it.nested(ListForm::forms) {
                    Pair(it.expectForm<SymbolForm>().sym, it.varargs(::monoTypeAnalyser))
                }
            }, {
                Pair(it.expectForm<SymbolForm>().sym, null)
            }) ?: TODO()

            val returnType = monoTypeAnalyser(it)

            it.expectEnd()

            TypedefExpr(sym, Typing(if (params != null) FnType(params, returnType) else returnType))
        }

        private val defxAnalyser: FormsAnalyser<ActionExpr> = {
            val sym = it.expectForm<SymbolForm>().sym

            TODO()
        }

        val actionExprAnalyser: FormsAnalyser<ActionExpr> = {
            val firstSym = it.expectForm<SymbolForm>().sym

            when (firstSym) {
                DEF -> defAnalyser(it)
                DEFX -> defxAnalyser(it)
                TYPEDEF -> typedefAnalyser(it)
                else -> TODO()
            }
        }

        companion object {
            private val DEF = Symbol.create("def")
            private val DEFX = Symbol.create("defx")
            private val TYPEDEF = Symbol.create("::")

            private val STR = Symbol.create("Str")
            private val BOOL = Symbol.create("Bool")
            private val INT = Symbol.create("Int")
            private val FLOAT = Symbol.create("Float")
            private val BIG_INT = Symbol.create("BigInt")
            private val BIG_FLOAT = Symbol.create("BigFloat")
        }
    }
}