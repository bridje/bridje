@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj.analyser

import brj.runtime.Ident
import brj.runtime.QSymbol
import brj.runtime.QSymbol.Companion.mkQSym
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.mkSym
import brj.runtime.SymbolKind.*
import brj.reader.Form
import brj.reader.ListForm
import brj.reader.QSymbolForm
import brj.reader.SymbolForm
import brj.runtime.DefMacroVar
import brj.runtime.PolyVar
import brj.types.*

internal val DO = mkSym("do")
internal val DEF = mkSym("def")
internal val DEFMACRO = mkSym("defmacro")
internal val DECL = mkSym("::")
internal val EFFECT = mkSym("!")
internal val POLY = mkSym(".")
internal val VARARGS = mkSym("&")

internal sealed class Expr
internal data class DefExpr(val sym: Symbol, val expr: ValueExpr, val isEffect: Boolean, val type: Type) : Expr()
internal data class PolyVarDefExpr(val polyVar: PolyVar, val implType: MonoType, val expr: ValueExpr) : Expr()
internal data class DefMacroExpr(val sym: Symbol, val expr: ValueExpr, val type: Type) : Expr()
internal data class VarDeclExpr(val sym: Symbol, val isEffect: Boolean, val type: Type) : Expr()
internal data class PolyVarDeclExpr(val sym: Symbol, val polyTypeVar: TypeVarType, val type: MonoType) : Expr()
internal data class TypeAliasDeclExpr(val sym: Symbol, val typeVars: List<TypeVarType>, val type: MonoType?) : Expr()
internal data class RecordKeyDeclExpr(val sym: Symbol, val typeVars: List<TypeVarType>, val type: MonoType) : Expr()
internal data class VariantKeyDeclExpr(val sym: Symbol, val typeVars: List<TypeVarType>, val paramTypes: List<MonoType>) : Expr()

internal sealed class DoOrExprResult
internal data class DoResult(val forms: List<Form>) : DoOrExprResult()
internal data class ExprResult(val expr: Expr) : DoOrExprResult()

internal data class ExprAnalyser(val resolver: Resolver,
                                 private val typeAnalyser: TypeAnalyser = TypeAnalyser(resolver)) {

    private fun firstSymAnalyser(sym: Symbol): (ParserState) -> ParserState? = {
        it.maybe {
            it.nested(ListForm::forms) {
                it.expectSym(sym)
                it
            }
        }
    }

    internal val defAnalyser =
        firstSymAnalyser(DEF).then {
            class Preamble(val sym: Symbol, val paramSyms: List<Symbol>? = null, val isEffect: Boolean = false)

            val preamble = it.or({
                it.maybe { it.expectSym(VAR_SYM) }?.let { Preamble(it) }
            }, {
                it.maybe { it.nested(ListForm::forms) { it } }?.let {
                    it.or({
                        it.maybe { it.expectSym(EFFECT) }?.let { _ ->
                            it.nested(ListForm::forms) {
                                Preamble(it.expectSym(VAR_SYM), paramSyms = it.varargs { it.expectSym(VAR_SYM) }, isEffect = true)
                            }
                        }
                    }, {
                        Preamble(it.expectSym(VAR_SYM), paramSyms = it.varargs { it.expectSym(VAR_SYM) })
                    })
                }
            }) ?: TODO()

            val locals = preamble.paramSyms?.map { it to LocalVar(it) }

            val bodyExpr = ValueExprAnalyser(resolver, (locals ?: emptyList()).toMap()).doAnalyser(it)

            val expr = if (locals != null) FnExpr(preamble.sym, locals.map { it.second }, bodyExpr) else bodyExpr

            DefExpr(preamble.sym, expr, preamble.isEffect, valueExprType(expr, resolver.resolveVar(preamble.sym)?.type?.monoType))
        }

    internal val declAnalyser = firstSymAnalyser(DECL).then {
        data class PolyVarPreamble(val polyTypeVar: TypeVarType)
        data class Preamble(val sym: Ident,
                            val typeVars: List<TypeVarType> = emptyList(),
                            val paramTypes: List<MonoType>? = null,
                            val isEffect: Boolean = false)

        val polyVarPreamble = it.maybe {
            it.nested(ListForm::forms) {
                it.expectSym(POLY)
                PolyVarPreamble(typeAnalyser.typeVarAnalyser(it)).also { _ -> it.expectEnd() }
            }
        }

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
                                Preamble(it.expectSym(VAR_SYM), paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser), isEffect = true)
                            }
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

        if (polyVarPreamble != null) {
            preamble.sym.symbolKind == VAR_SYM || TODO()
            preamble.sym as Symbol

            val polyTypeVar = polyVarPreamble.polyTypeVar
            val returnType = typeAnalyser.monoTypeAnalyser(it)
            it.expectEnd()

            PolyVarDeclExpr(preamble.sym, polyTypeVar,
                if (preamble.paramTypes != null) FnType(preamble.paramTypes, returnType) else returnType)
        } else {
            preamble.sym as Symbol

            when (preamble.sym.symbolKind) {
                VAR_SYM -> {
                    val returnType = typeAnalyser.monoTypeAnalyser(it)
                    val type = Type(if (preamble.paramTypes == null) returnType else FnType(preamble.paramTypes, returnType), effects = emptySet())
                    VarDeclExpr(preamble.sym, preamble.isEffect, type)
                }

                RECORD_KEY_SYM -> RecordKeyDeclExpr(preamble.sym, preamble.typeVars, typeAnalyser.monoTypeAnalyser(it))
                VARIANT_KEY_SYM -> VariantKeyDeclExpr(preamble.sym, preamble.typeVars, it.varargs(typeAnalyser::monoTypeAnalyser))
                TYPE_ALIAS_SYM -> TypeAliasDeclExpr(preamble.sym, preamble.typeVars, if (it.forms.isNotEmpty()) typeAnalyser.monoTypeAnalyser(it) else null)
            }.also { _ -> it.expectEnd() }
        }
    }

    private val defMacroAnalyser = firstSymAnalyser(DEFMACRO).then {
        data class Preamble(val sym: Symbol, val fixedParamSyms: List<Symbol>, val varargsSym: Symbol?)

        val preamble = it.nested(ListForm::forms) {
            val sym = it.expectSym(VAR_SYM)
            Preamble(sym,
                it.many { it.expectSym(VAR_SYM).takeIf { it != VARARGS } },
                it.maybe { it.expectSym(VAR_SYM) })
                .also { _ -> it.expectEnd() }
        }

        val locals = (preamble.fixedParamSyms + listOfNotNull(preamble.varargsSym)).map { it to LocalVar(it) }

        val bodyExpr = ValueExprAnalyser(resolver, locals.toMap()).doAnalyser(it)

        val expr = FnExpr(preamble.sym, locals.map { it.second }, bodyExpr)

        val formType = TypeAliasType(resolver.resolveTypeAlias(mkQSym("brj.forms/Form"))!!, emptyList())

        val exprType = valueExprType(expr, FnType(preamble.fixedParamSyms.map { formType } + listOfNotNull(preamble.varargsSym).map { VectorType(formType) }, formType))

        DefMacroExpr(preamble.sym, expr, exprType)
    }

    internal fun analyseExpr(form: Form): DoOrExprResult =
        ParserState(listOf(form)).or(
            firstSymAnalyser(DO).then { DoResult(it.forms) },
            defAnalyser.then(::ExprResult),
            declAnalyser.then(::ExprResult),
            defMacroAnalyser.then(::ExprResult),

            {
                val forms = it.forms
                if (forms.isNotEmpty()) {
                    val macroVar = when (val firstForm = forms[0]) {
                        is SymbolForm -> resolver.resolveVar(firstForm.sym) as? DefMacroVar
                        is QSymbolForm -> resolver.resolveVar(firstForm.sym) as? DefMacroVar
                        else -> null
                    } ?: TODO()

                    analyseExpr(macroVar.evalMacro(forms.drop(1)))
                }
                TODO()
            }
        ) ?: TODO()
}
