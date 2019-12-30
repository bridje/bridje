@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj.analyser

import brj.reader.*
import brj.reader.Form
import brj.reader.ListForm
import brj.reader.QSymbolForm
import brj.reader.SymbolForm
import brj.runtime.*
import brj.runtime.SymKind.*
import brj.types.*

internal val DO = Symbol(ID, "do")
internal val DEF = Symbol(ID, "def")
internal val DEFMACRO = Symbol(ID, "defmacro")
internal val DECL = Symbol(ID, "::")
internal val EFFECT = Symbol(ID, "!")
internal val POLY = Symbol(ID, ".")
internal val VARARGS = Symbol(ID, "&")

internal sealed class Expr

internal data class VarDeclExpr(val sym: Symbol, val isEffect: Boolean, val type: Type) : Expr()
internal data class TypeAliasDeclExpr(val sym: Symbol, val typeVars: List<TypeVarType>, val type: MonoType?) : Expr()

internal data class DefExpr(val sym: Symbol, val expr: ValueExpr, val type: Type) : Expr()
internal data class DefMacroExpr(val sym: Symbol, val expr: FnExpr, val type: Type) : Expr()

internal data class PolyVarDeclExpr(val sym: Symbol, val primaryTVs: List<TypeVarType>, val secondaryTVs: List<TypeVarType>, val type: MonoType) : Expr()
internal data class PolyVarDefExpr(val polyVar: PolyVar, val primaryPolyTypes: List<MonoType>, val secondaryPolyTypes: List<MonoType>, val expr: ValueExpr) : Expr()

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
            data class PolyVarPreamble(val polyTypes: List<MonoType>)
            class Preamble(val sym: Symbol, val paramSyms: List<Symbol>? = null)

            val polyVarPreamble = it.maybe {
                it.nested(ListForm::forms) {
                    it.expectSym(POLY)
                    PolyVarPreamble(it.varargs { typeAnalyser.monoTypeAnalyser(it) })
                }
            }

            val preamble = it.or({
                it.maybe { it.expectSym(ID) }?.let { Preamble(it) }
            }, {
                it.maybe { it.nested(ListForm::forms) { it } }?.let {
                    Preamble(it.expectSym(ID), paramSyms = it.varargs { it.expectSym(ID) })
                }
            }) ?: TODO()

            val locals = preamble.paramSyms?.map { it to LocalVar(it) }

            val bodyExpr = ValueExprAnalyser(resolver, (locals ?: emptyList()).toMap()).doAnalyser(it)

            val expr = if (locals != null) FnExpr(preamble.sym, locals.map { it.second }, bodyExpr) else bodyExpr

            if (polyVarPreamble != null) {
                val polyVar = resolver.resolveVar(preamble.sym) as? PolyVar ?: TODO()
                val polyConstraint = polyVar.polyConstraint
                val primaryTVs = polyConstraint.primaryTVs
                val secondaryTVs = polyConstraint.secondaryTVs

                val polyTypes = polyVarPreamble.polyTypes

                polyTypes.size == primaryTVs.size + secondaryTVs.size || TODO()

                // TODO type-check the expr
                PolyVarDefExpr(polyVar, polyTypes.take(primaryTVs.size), polyTypes.drop(primaryTVs.size), expr)

            } else {
                val type = valueExprType(expr, resolver.resolveVar(preamble.sym)?.type?.monoType)

                DefExpr(preamble.sym, expr, type)
            }
        }

    internal val declAnalyser = firstSymAnalyser(DECL).then {
        data class PolyVarPreamble(val polyTypeVars: List<TypeVarType>)

        data class Preamble(val sym: Ident,
                            val typeVars: List<TypeVarType> = emptyList(),
                            val paramTypes: List<MonoType>? = null,
                            val isEffect: Boolean = false)

        val polyVarPreamble = it.maybe {
            it.nested(ListForm::forms) {
                it.expectSym(POLY)
                PolyVarPreamble(it.varargs { typeAnalyser.typeVarAnalyser(it) })
            }
        }

        val preamble = it.or({
            // (:: <sym> <type>)
            it.maybe { it.expectSym() }?.let { sym -> Preamble(sym) }
        }, {
            it.maybe { it.expectForm<ListForm>() }?.let { listForm ->
                it.nested(listForm.forms) {
                    it.maybe { it.expectIdent() }?.let { sym ->
                        when (sym) {
                            is Symbol ->
                                when (sym.kind) {
                                    // (:: (foo Int) Str)
                                    ID -> Preamble(sym, paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser))

                                    // (:: (:Ok a) a)
                                    // (:: (Maybe a) (+ (:Ok a) :Nil))
                                    RECORD, VARIANT, TYPE -> Preamble(sym, typeVars = it.varargs(typeAnalyser::typeVarAnalyser))
                                }.also { _ -> it.expectEnd() }
                            is QSymbol -> {
                                Preamble(sym, paramTypes = it.varargs(typeAnalyser::monoTypeAnalyser))
                            }
                        }
                    }
                }
            }
        }) ?: TODO()

        if (polyVarPreamble != null) {
            preamble.sym.kind == ID || TODO()
            preamble.sym as Symbol

            val polyTypeVars = polyVarPreamble.polyTypeVars
            val returnType = typeAnalyser.monoTypeAnalyser(it)
            it.expectEnd()

            val type = if (preamble.paramTypes != null) FnType(preamble.paramTypes, returnType) else returnType
            val (primaryTVs, secondaryTVs) = if (preamble.sym.local.startsWith("."))
                Pair(polyTypeVars.take(1), polyTypeVars.drop(1))
            else
                Pair(polyTypeVars, emptyList())

            PolyVarDeclExpr(preamble.sym, primaryTVs, secondaryTVs, type)

        } else {
            preamble.sym as? Symbol ?: TODO()

            when (preamble.sym.kind) {
                ID -> {
                    val (returnType, isEffect) = it.or({
                        // (:: (println! Str) (! Void))
                        it.maybe {
                            it.nested(ListForm::forms) { it.expectSym(EFFECT); it }
                        }?.let {
                            Pair(typeAnalyser.monoTypeAnalyser(it), true)
                        }
                    }, {
                        Pair(typeAnalyser.monoTypeAnalyser(it), false)
                    }) ?: TODO()

                    val type = Type(if (preamble.paramTypes == null) returnType else FnType(preamble.paramTypes, returnType))
                    VarDeclExpr(preamble.sym, isEffect, type)
                }

                RECORD -> RecordKeyDeclExpr(preamble.sym, preamble.typeVars, typeAnalyser.monoTypeAnalyser(it))
                VARIANT -> VariantKeyDeclExpr(preamble.sym, preamble.typeVars, it.varargs(typeAnalyser::monoTypeAnalyser))
                TYPE -> TypeAliasDeclExpr(preamble.sym, preamble.typeVars, if (it.forms.isNotEmpty()) typeAnalyser.monoTypeAnalyser(it) else null)
            }.also { _ -> it.expectEnd() }
        }
    }

    private val defMacroAnalyser = firstSymAnalyser(DEFMACRO).then {
        data class Preamble(val sym: Symbol, val fixedParamSyms: List<Symbol>, val varargsSym: Symbol?)

        val preamble = it.nested(ListForm::forms) {
            val sym = it.expectSym(ID)
            Preamble(sym,
                it.many { it.expectSym(ID).takeIf { it != VARARGS } },
                it.maybe { it.expectSym(ID) })
                .also { _ -> it.expectEnd() }
        }

        val locals = (preamble.fixedParamSyms + listOfNotNull(preamble.varargsSym)).map { it to LocalVar(it) }

        val bodyExpr = ValueExprAnalyser(resolver, locals.toMap()).doAnalyser(it)

        val expr = FnExpr(preamble.sym, locals.map { it.second }, bodyExpr)

        val formType = TypeAliasType(resolver.resolveTypeAlias(FORM)!!, emptyList())

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
