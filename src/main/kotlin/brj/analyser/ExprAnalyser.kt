package brj.analyser

import brj.*
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.SymbolKind.*
import brj.types.*

internal val DO = mkSym("do")
internal val DEF = mkSym("def")
internal val DEFMACRO = mkSym("defmacro")
internal val DECL = mkSym("::")
internal val EFFECT = mkSym("!")

internal sealed class Expr
internal data class DefExpr(val sym: QSymbol, val expr: ValueExpr, val type: Type) : Expr()
internal data class DefMacroExpr(val sym: QSymbol, val expr: ValueExpr, val type: Type) : Expr()
internal data class VarDeclExpr(val sym: QSymbol, val type: Type) : Expr()
internal data class PolyVarDeclExpr(val sym: QSymbol, val typeVar: TypeVarType, val type: Type) : Expr()
internal data class TypeAliasDeclExpr(val typeAlias: TypeAlias) : Expr()
internal data class RecordKeyDeclExpr(val recordKey: RecordKey) : Expr()
internal data class VariantKeyDeclExpr(val variantKey: VariantKey) : Expr()

internal sealed class DoOrExprResult
internal data class DoResult(val forms: List<Form>) : DoOrExprResult()
internal data class ExprResult(val expr: Expr) : DoOrExprResult()

// TODO allow forward declarations of type aliases

internal data class ExprAnalyser(val env: Env, val nsEnv: NSEnv,
                                 private val typeAnalyser: TypeAnalyser = TypeAnalyser(env, nsEnv)) {
    private fun nsQSym(sym: Symbol) = mkQSym(nsEnv.ns, sym)

    internal fun analyseDecl(it: ParserState): Expr {
        data class Preamble(val sym: QSymbol, val typeVars: List<TypeVarType> = emptyList(), val paramTypes: List<MonoType>? = null, val effect: Boolean = false)

        val preamble = it.or({
            it.maybe { it.expectSym() }?.let { sym ->
                // (:: <sym> <type>)
                when (sym.symbolKind) {
                    VAR_SYM, RECORD_KEY_SYM, VARIANT_KEY_SYM, TYPE_ALIAS_SYM -> Preamble(nsQSym(sym))
                    POLYVAR_SYM -> TODO("polyvar sym needs a type-var")
                }
            }
        }, {
            it.maybe { it.expectForm<ListForm>() }?.let { listForm ->
                it.nested(listForm.forms) {
                    it.or({
                        // (:: (! (println! Str) Void))
                        it.maybe { it.expectSym(EFFECT) }?.let { _ ->
                            it.nested(ListForm::forms) {
                                Preamble(nsQSym(it.expectSym(VAR_SYM)), paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser), effect = true)
                            }
                        }
                    }, {
                        it.maybe { it.expectSym() }?.let { sym ->
                            when (sym.symbolKind) {
                                // (:: (foo Int) Str)
                                VAR_SYM -> Preamble(nsQSym(sym), paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser))

                                // (:: (:Ok a) a)
                                // (:: (Maybe a) (+ (:Ok a) :Nil))
                                RECORD_KEY_SYM, VARIANT_KEY_SYM, TYPE_ALIAS_SYM -> Preamble(nsQSym(sym), typeVars = it.varargs(typeAnalyser::typeVarAnalyser))

                                // (:: (.mzero a) a)
                                POLYVAR_SYM -> Preamble(nsQSym(sym), listOf(typeAnalyser.typeVarAnalyser(it)))
                            }.also { _ -> it.expectEnd() }
                        }
                    }, {
                        // (:: ((.count a) a) Int)
                        it.maybe { it.expectForm<ListForm>() }?.let { listForm ->
                            it.nested(listForm.forms) {
                                Pair(it.expectSym(POLYVAR_SYM), typeAnalyser.typeVarAnalyser(it))
                                    .also { _ -> it.expectEnd() }
                            }
                        }?.let { (sym, tv) -> Preamble(nsQSym(sym), listOf(tv), it.varargs(typeAnalyser::monoTypeAnalyser)) }
                    })
                }
            }
        }) ?: TODO()

        return when (preamble.sym.symbolKind) {
            VAR_SYM -> {
                val returnType = typeAnalyser.monoTypeAnalyser(it)
                val type = Type(if (preamble.paramTypes == null) returnType else FnType(preamble.paramTypes, returnType),
                    effects = if (preamble.effect) setOf(preamble.sym) else emptySet())
                VarDeclExpr(preamble.sym, type)
            }

            POLYVAR_SYM -> {
                val returnType = typeAnalyser.monoTypeAnalyser(it)
                val type = Type(if (preamble.paramTypes == null) returnType else FnType(preamble.paramTypes, returnType))
                PolyVarDeclExpr(preamble.sym, preamble.typeVars.first(), type)
            }

            RECORD_KEY_SYM -> RecordKeyDeclExpr(RecordKey(preamble.sym, preamble.typeVars, typeAnalyser.monoTypeAnalyser(it)))
            VARIANT_KEY_SYM -> VariantKeyDeclExpr(VariantKey(preamble.sym, preamble.typeVars, it.varargs(typeAnalyser::monoTypeAnalyser)))
            TYPE_ALIAS_SYM -> {
                val type = if (it.forms.isNotEmpty()) typeAnalyser.monoTypeAnalyser(it) else null

                TypeAliasDeclExpr(
                    nsEnv.typeAliases[preamble.sym.base]?.also { (it as TypeAlias_).type = type }
                        ?: TypeAlias_(preamble.sym, preamble.typeVars, type))
            }
        }.also { _ -> it.expectEnd() }
    }

    internal fun analyseDef(it: ParserState): DefExpr {
        data class Preamble(val sym: QSymbol, val paramSyms: List<Symbol>? = null, val effect: Boolean = false)

        val preamble = it.or({
            it.maybe { it.expectSym(VAR_SYM) }?.let { Preamble(nsQSym(it)) }
        }, {
            it.maybe { it.expectForm<ListForm>() }?.let { lf ->
                it.nested(lf.forms) {
                    it.or({
                        it.maybe { it.expectSym(EFFECT) }?.let { _ ->
                            it.nested(ListForm::forms) {
                                Preamble(nsQSym(it.expectSym(VAR_SYM)), it.varargs { it.expectSym(VAR_SYM) }, effect = true)
                            }
                        }
                    }, {
                        Preamble(nsQSym(it.expectSym(VAR_SYM)), it.varargs { it.expectSym(VAR_SYM) })
                    })
                }
            }
        })
            ?: TODO()

        val locals = preamble.paramSyms?.map { it to LocalVar(it) }

        val bodyExpr = ValueExprAnalyser(env, nsEnv, (locals ?: emptyList()).toMap()).doAnalyser(it)

        val expr = if (locals != null) FnExpr(preamble.sym.base, locals.map { it.second }, bodyExpr) else bodyExpr

        val valueExprType = valueExprType(expr, nsEnv.vars[preamble.sym.base]?.type?.monoType)
        val effects = if (preamble.effect) setOf(preamble.sym) else valueExprType.effects

        return if (effects.isEmpty())
            DefExpr(preamble.sym, expr, valueExprType)
        else {
            if (expr !is FnExpr) TODO()
            DefExpr(preamble.sym, expr, valueExprType.copy(effects = effects))
        }
    }

    private fun analyseDefMacro(it: ParserState): DefMacroExpr {
        data class Preamble(val sym: QSymbol, val paramSyms: List<Symbol>)

        val preamble = it.nested(ListForm::forms) {
            val sym = nsQSym(it.expectSym(VAR_SYM))
            Preamble(sym, it.varargs { it.expectSym(VAR_SYM) })
        }

        val locals = preamble.paramSyms.map { it to LocalVar(it) }

        val bodyExpr = ValueExprAnalyser(env, nsEnv, locals.toMap()).doAnalyser(it)

        val expr = FnExpr(preamble.sym.base, locals.map { it.second }, bodyExpr)

        // TODO this needs to be done automagically
        val formTypeAlias = TypeAlias_(mkQSym("brj.forms/Form"), emptyList(), null)
        val formType = TypeAliasType(formTypeAlias, emptyList())
        formTypeAlias.type = formType

        val exprType = valueExprType(expr, FnType(generateSequence { formType }.take(locals.size).toList(), formType))

        return DefMacroExpr(preamble.sym, expr, exprType)
    }

    internal fun analyseExpr(form: Form): DoOrExprResult {
        if (form !is ListForm) TODO()

        val state = ParserState(form.forms)

        return when (state.expectSym()) {
            DO -> DoResult(state.forms)
            DEF -> ExprResult(analyseDef(state))
            DECL -> ExprResult(analyseDecl(state))
            DEFMACRO -> ExprResult(analyseDefMacro(state))
            else -> TODO()
        }
    }
}
