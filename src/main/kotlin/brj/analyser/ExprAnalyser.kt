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
internal val POLY = mkSym(".")
internal val VARARGS = mkSym("&")

internal sealed class Expr
internal data class DefExpr(val sym: QSymbol, val expr: ValueExpr, val type: Type) : Expr()
internal data class DefMacroExpr(val sym: QSymbol, val expr: ValueExpr, val type: Type) : Expr()
internal data class VarDeclExpr(val sym: QSymbol, val type: Type) : Expr()
internal data class PolyVarDeclExpr(val sym: QSymbol, val typeVar: TypeVarType, val type: Type) : Expr()
internal data class TypeAliasDeclExpr(val typeAlias: TypeAlias) : Expr()
internal data class RecordKeyDeclExpr(val recordKey: RecordKey) : Expr()
internal data class VariantKeyDeclExpr(val variantKey: VariantKey) : Expr()
internal data class JavaImportDeclExpr(val javaImport: JavaImport) : Expr()

internal sealed class DoOrExprResult
internal data class DoResult(val forms: List<Form>) : DoOrExprResult()
internal data class ExprResult(val expr: Expr) : DoOrExprResult()

internal data class ExprAnalyser(val env: RuntimeEnv, val nsEnv: NSEnv,
                                 val macroEvaluator: MacroEvaluator,
                                 private val typeAnalyser: TypeAnalyser = TypeAnalyser(env, nsEnv)) {
    private fun nsQSym(sym: Symbol) = mkQSym(nsEnv.ns, sym)

    internal fun analyseDecl(it: ParserState): Expr {
        data class Preamble(val sym: Ident?,
                            val polyTypeVar: TypeVarType? = null,
                            val typeVars: List<TypeVarType> = emptyList(),
                            val paramTypes: List<MonoType>? = null,
                            val effect: Boolean = false)

        val preamble = it.or({
            // (:: <sym> <type>)
            it.maybe { it.expectSym() }?.let { sym -> Preamble(sym) }
        }, {
            it.maybe { it.expectForm<ListForm>() }?.let { listForm ->
                it.nested(listForm.forms) {
                    it.or({
                        // (:: (! (println! Str) Void))
                        it.maybe { it.expectSym(EFFECT) }?.let { _ ->
                            it.nested(ListForm::forms) {
                                // TODO interop syms can be effects too
                                Preamble(it.expectSym(VAR_SYM), paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser), effect = true)
                            }
                        }
                    }, {
                        it.maybe { it.expectSym(POLY) }?.let { _ ->
                            // (:: (. a) (count a) Int)
                            Preamble(sym = null, polyTypeVar = typeAnalyser.typeVarAnalyser(it))
                        }
                    }, {
                        it.maybe { it.expectIdent() }?.let { sym ->
                            when (sym) {
                                is Symbol ->
                                    when (sym.symbolKind) {
                                        // (:: (foo Int) Str)
                                        VAR_SYM -> Preamble(sym, paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser))

                                        // (:: (:Ok a) a)
                                        // (:: (Maybe a) (+ (:Ok a) :Nil))
                                        RECORD_KEY_SYM, VARIANT_KEY_SYM, TYPE_ALIAS_SYM -> Preamble(sym, typeVars = it.varargs(typeAnalyser::typeVarAnalyser))
                                    }.also { _ -> it.expectEnd() }
                                is QSymbol -> {
                                    Preamble(sym, paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser))
                                }
                            }
                        }
                    })
                }
            }
        }) ?: TODO()

        return when (preamble.sym) {
            null -> {
                data class PolyPreamble(val sym: Symbol, val paramTypes: List<MonoType>?)

                val polyPreamble = it.or({
                    it.maybe { it.expectSym(VAR_SYM) }?.let { sym -> PolyPreamble(sym, null) }
                }, {
                    it.maybe { it.expectForm<ListForm>() }?.forms?.let { forms -> it.nested(forms) {
                        PolyPreamble(it.expectSym(VAR_SYM), it.varargs(typeAnalyser::monoTypeAnalyser))
                    } }
                }) ?: TODO()

                val qSym = nsQSym(polyPreamble.sym)
                val polyTypeVar = preamble.polyTypeVar!!
                val returnType = typeAnalyser.monoTypeAnalyser(it)
                it.expectEnd()

                PolyVarDeclExpr(
                    qSym, polyTypeVar,
                    Type(
                        if (polyPreamble.paramTypes != null) FnType(polyPreamble.paramTypes, returnType) else returnType,
                        mapOf(preamble.polyTypeVar to setOf(qSym))))
            }

            is Symbol ->
                when (preamble.sym.symbolKind) {
                    VAR_SYM -> {
                        val returnType = typeAnalyser.monoTypeAnalyser(it)
                        val qsym = nsQSym(preamble.sym)
                        val type = Type(if (preamble.paramTypes == null) returnType else FnType(preamble.paramTypes, returnType),
                            effects = if (preamble.effect) setOf(qsym) else emptySet())
                        VarDeclExpr(qsym, type)
                    }

                    RECORD_KEY_SYM -> RecordKeyDeclExpr(RecordKey(nsQSym(preamble.sym), preamble.typeVars, typeAnalyser.monoTypeAnalyser(it)))
                    VARIANT_KEY_SYM -> VariantKeyDeclExpr(VariantKey(nsQSym(preamble.sym), preamble.typeVars, it.varargs(typeAnalyser::monoTypeAnalyser)))
                    TYPE_ALIAS_SYM -> {
                        val type = if (it.forms.isNotEmpty()) typeAnalyser.monoTypeAnalyser(it) else null

                        TypeAliasDeclExpr(
                            nsEnv.typeAliases[preamble.sym]?.also { (it as TypeAlias_).type = type }
                                ?: TypeAlias_(nsQSym(preamble.sym), preamble.typeVars, type))
                    }
                }.also { _ -> it.expectEnd() }
            is QSymbol -> {
                val alias = nsEnv.aliases[preamble.sym.ns] as? JavaAlias ?: TODO()
                val qsym = mkQSym(alias.ns, preamble.sym.base)
                val returnType = typeAnalyser.monoTypeAnalyser(it)
                val type = Type(if (preamble.paramTypes == null) returnType else FnType(preamble.paramTypes, returnType),
                    effects = if (preamble.effect) setOf(qsym) else emptySet())

                JavaImportDeclExpr(JavaImport(qsym, alias.clazz, preamble.sym.base.baseStr, type))
            }
        }
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

        val bodyExpr = ValueExprAnalyser(env, nsEnv, macroEvaluator, (locals ?: emptyList()).toMap()).doAnalyser(it)

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
        data class Preamble(val sym: QSymbol, val fixedParamSyms: List<Symbol>, val varargsSym: Symbol?)

        val preamble = it.nested(ListForm::forms) {
            val sym = nsQSym(it.expectSym(VAR_SYM))
            Preamble(sym,
                it.many { it.expectSym(VAR_SYM).takeIf { it != VARARGS } },
                it.maybe { it.expectSym(VAR_SYM) })
                .also { _ -> it.expectEnd() }
        }

        val locals = (preamble.fixedParamSyms + listOfNotNull(preamble.varargsSym)).map { it to LocalVar(it) }

        val bodyExpr = ValueExprAnalyser(env, nsEnv, macroEvaluator, locals.toMap()).doAnalyser(it)

        val expr = FnExpr(preamble.sym.base, locals.map { it.second }, bodyExpr)

        val formType = TypeAliasType(resolveTypeAlias(env, nsEnv, mkQSym("brj.forms/Form"))!!, emptyList())

        val exprType = valueExprType(expr, FnType(preamble.fixedParamSyms.map { formType } + listOfNotNull(preamble.varargsSym).map { VectorType(formType) }, formType))

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

            else -> {
                val forms = form.forms
                if (forms.isNotEmpty()) {
                    val firstForm = forms[0]
                    val macroVar = when (firstForm) {
                        is SymbolForm -> resolve(env, nsEnv, firstForm.sym) as? DefMacroVar
                        is QSymbolForm -> resolve(env, nsEnv, firstForm.sym) as? DefMacroVar
                        else -> null
                    } ?: TODO()

                    return analyseExpr(macroEvaluator.evalMacro(env, macroVar, forms.drop(1)))
                }

                TODO()
            }
        }
    }
}
