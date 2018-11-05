package brj

import brj.Analyser.AnalyserState
import brj.Form.*
import brj.Types.MonoType
import brj.Types.MonoType.*
import brj.Types.Typing
import brj.ValueExpr.FnExpr
import brj.ValueExpr.ValueExprAnalyser

@Suppress("NestedLambdaShadowedImplicitParameter")
internal class ActionExprAnalyser(val brjEnv: BrjEnv, val nsEnv: BrjEnv.NSEnv) {
    data class DefExpr(val sym: Symbol, val expr: ValueExpr, val typing: Typing)
    data class TypeDefExpr(val sym: Symbol, val typing: Typing)

    data class DefDataExpr(val sym: Symbol, val typeParams: List<TypeVarType>?, val constructors: List<DefDataConstructor> = emptyList()) {
        data class DefDataConstructor(val kw: Keyword, val params: List<MonoType>?)
    }

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

    class TypeAnalyser {
        val tvMapping: MutableMap<Symbol, TypeVarType> = mutableMapOf()

        private fun tv(sym: Symbol): TypeVarType? =
            if (Character.isLowerCase(sym.name.first())) {
                tvMapping.getOrPut(sym) { TypeVarType() }
            } else null

        fun monoTypeAnalyser(it: AnalyserState): MonoType {
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
                            tv(form.sym) ?: TODO()
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

        fun tvAnalyser(it: AnalyserState): TypeVarType =
            tv(it.expectForm<SymbolForm>().sym) ?: TODO()
    }

    val typeDefAnalyser: FormsAnalyser<TypeDefExpr> = {
        val typeAnalyser = TypeAnalyser()

        val (sym, params) = defDataSigAnalyser(it, typeAnalyser)

        val returnType = typeAnalyser.monoTypeAnalyser(it)

        it.expectEnd()

        TypeDefExpr(sym, Typing(if (params != null) FnType(params, returnType) else returnType))
    }

    fun defDataSigAnalyser(it: AnalyserState, typeAnalyser: TypeAnalyser = TypeAnalyser()): Pair<Symbol, List<TypeVarType>?> {
        val form = it.expectForm<Form>()

        return when (form) {
            is Form.ListForm -> {
                it.nested(form.forms) {
                    Pair(it.expectForm<Form.SymbolForm>().sym, it.varargs(typeAnalyser::tvAnalyser))
                }
            }

            is Form.SymbolForm -> {
                Pair(form.sym, null)
            }

            else -> TODO()
        }
    }

    val defDataAnalyser: FormsAnalyser<DefDataExpr> = {
        val typeAnalyser = TypeAnalyser()

        val (sym, typeParams) = defDataSigAnalyser(it, typeAnalyser)

        val constructors = it.varargs {
            val form = it.expectForm<Form>()

            when (form) {
                is ListForm -> {
                    it.nested(form.forms) {
                        DefDataExpr.DefDataConstructor(it.expectForm<KeywordForm>().kw, it.varargs(typeAnalyser::monoTypeAnalyser))
                    }
                }

                is KeywordForm -> {
                    DefDataExpr.DefDataConstructor(form.kw, null)
                }

                else -> TODO()
            }
        }

        DefDataExpr(sym, typeParams, constructors)
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