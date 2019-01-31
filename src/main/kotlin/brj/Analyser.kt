package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import java.math.BigDecimal
import java.math.BigInteger

typealias FormsAnalyser<R> = (AnalyserState) -> R

sealed class AnalyserError : Exception() {
    object ExpectedForm : AnalyserError()
    data class UnexpectedForms(val forms: List<Form>) : AnalyserError()
    object ExpectedSymbol : AnalyserError()
}

data class AnalyserState(var forms: List<Form>) {
    fun <R> varargs(a: FormsAnalyser<R>): List<R> {
        val ret: MutableList<R> = mutableListOf()

        while (forms.isNotEmpty()) {
            ret += a(this)
        }

        return ret
    }

    fun expectEnd() {
        if (forms.isNotEmpty()) {
            throw AnalyserError.UnexpectedForms(forms)
        }
    }

    inline fun <reified F : Form> expectForm(): F =
        if (forms.isNotEmpty()) {
            val firstForm = forms.first() as? F ?: throw AnalyserError.ExpectedForm
            forms = forms.drop(1)
            firstForm
        } else {
            throw AnalyserError.ExpectedForm
        }

    fun <R> maybe(a: FormsAnalyser<R?>): R? =
        try {
            val newState = copy()
            val res = a(newState)

            if (res != null) {
                forms = newState.forms
            }

            res
        } catch (e: AnalyserError) {
            null
        }

    fun <R> or(vararg analysers: FormsAnalyser<R>): R? {
        for (a in analysers) {
            val res = maybe(a)
            if (res != null) {
                return res
            }
        }

        return null
    }

    fun <R> nested(forms: List<Form>, a: FormsAnalyser<R>): R = a(AnalyserState(forms))

    inline fun <reified F : Form, R> nested(f: (F) -> List<Form>, noinline a: FormsAnalyser<R>): R = nested(f(expectForm()), a)

    fun expectSym(expectedSym: Symbol): Symbol {
        val actualSym = expectForm<SymbolForm>().sym
        if (expectedSym == actualSym) return expectedSym else throw AnalyserError.ExpectedSymbol
    }
}

class LocalVar(val sym: Symbol) {
    override fun toString() = "LV($sym)"
}

sealed class ValueExpr

data class BooleanExpr(val boolean: Boolean) : ValueExpr()
data class StringExpr(val string: String) : ValueExpr()
data class IntExpr(val int: Long) : ValueExpr()
data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
data class FloatExpr(val float: Double) : ValueExpr()
data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()

data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()
data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()

data class RecordEntry(val attribute: Attribute, val expr: ValueExpr)
data class RecordExpr(val entries: List<RecordEntry>) : ValueExpr()

data class CallExpr(val f: ValueExpr, val args: List<ValueExpr>) : ValueExpr()

data class FnExpr(val fnName: Symbol? = null, val params: List<LocalVar>, val expr: ValueExpr) : ValueExpr()
data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()
data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

data class LetBinding(val localVar: LocalVar, val expr: ValueExpr)
data class LetExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()

data class LoopExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()
data class RecurExpr(val exprs: List<Pair<LocalVar, ValueExpr>>) : ValueExpr()

data class CaseClause(val constructor: DataTypeConstructor, val bindings: List<LocalVar>?, val bodyExpr: ValueExpr)
data class CaseExpr(val expr: ValueExpr, val clauses: List<CaseClause>, val defaultExpr: ValueExpr?) : ValueExpr()

data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()
data class GlobalVarExpr(val globalVar: GlobalVar) : ValueExpr()

data class DefExpr(val sym: Symbol, val expr: ValueExpr, val type: Type)
data class TypeDefExpr(val sym: Symbol, val type: Type)

internal val IF = mkSym("if")
internal val FN = mkSym("fn")
internal val LET = mkSym("let")
internal val CASE = mkSym("case")
internal val LOOP = mkSym("loop")
internal val RECUR = mkSym("recur")

internal val DO = mkSym("do")
internal val DEF = mkSym("def")
internal val TYPE_DEF = mkSym("::")

internal val NS = mkSym("ns")
internal val REFERS = mkSym(":refers")
internal val ALIASES = mkSym(":aliases")
internal val IMPORTS = mkSym(":imports")
internal val JAVA = mkSym("java")

internal class NSAnalyser(val ns: Symbol) {
    fun refersAnalyser(it: AnalyserState): Map<Symbol, QSymbol> {
        val refers = mutableMapOf<Symbol, QSymbol>()

        it.varargs {
            val nsSym = it.expectForm<SymbolForm>().sym
            it.nested(SetForm::forms) {
                it.varargs {
                    val sym = it.expectForm<SymbolForm>().sym
                    refers[sym] = mkQSym("${if (sym.isKeyword) ":" else ""}${nsSym.baseStr}/${sym.baseStr}")
                }
            }
        }

        return refers
    }

    fun aliasesAnalyser(it: AnalyserState): Map<Symbol, Symbol> {
        val aliases = mutableMapOf<Symbol, Symbol>()

        it.varargs {
            aliases[it.expectForm<SymbolForm>().sym] = it.expectForm<SymbolForm>().sym
        }

        return aliases
    }

    fun javaImportsAnalyser(it: AnalyserState): Map<QSymbol, JavaImport> {
        val javaImports = mutableMapOf<QSymbol, JavaImport>()

        it.varargs {
            val alias = it.expectForm<SymbolForm>().sym

            it.nested(ListForm::forms) {
                it.expectSym(JAVA)
                val clazz = Class.forName(it.expectForm<SymbolForm>().sym.baseStr)

                it.varargs {
                    it.nested(ListForm::forms) {
                        it.expectSym(TYPE_DEF)
                        val (sym, type) = ActionExprAnalyser(Env(), NSEnv(ns)).typeDefAnalyser(it)

                        val importSym = QSymbol.mkQSym("$alias/$sym")
                        javaImports[importSym] = JavaImport(clazz, importSym, type)
                    }
                }
            }
        }

        return javaImports
    }

    internal fun analyseNS(state: AnalyserState): NSEnv =
        state.nested(ListForm::forms) {
            it.expectSym(NS)
            var nsEnv = NSEnv(it.expectSym(ns))
            val ana = NSAnalyser(nsEnv.ns)

            if (it.forms.isNotEmpty()) {
                it.nested(RecordForm::forms) {
                    it.varargs {
                        val sym = it.expectForm<SymbolForm>().sym
                        nsEnv = when (sym) {
                            REFERS -> {
                                nsEnv.copy(refers = it.nested(RecordForm::forms, ana::refersAnalyser))
                            }

                            ALIASES -> {
                                nsEnv.copy(aliases = it.nested(RecordForm::forms, ana::aliasesAnalyser))
                            }

                            IMPORTS -> {
                                nsEnv.copy(javaImports = it.nested(RecordForm::forms, ana::javaImportsAnalyser))
                            }

                            else -> TODO()
                        }
                    }
                }

            }

            nsEnv
        }
}

@Suppress("NestedLambdaShadowedImplicitParameter")
internal data class ValueExprAnalyser(val env: Env, val nsEnv: NSEnv, val locals: Map<Symbol, LocalVar> = emptyMap(), val loopLocals: List<LocalVar>? = null) {
    private fun resolve(sym: Symbol) = resolve(env, nsEnv, sym)
    private fun resolve(sym: QSymbol) = resolve(env, nsEnv, sym)

    private fun symAnalyser(form: SymbolForm): ValueExpr {
        return ((locals[form.sym]?.let { LocalVarExpr(it) })
            ?: resolve(form.sym)?.let(::GlobalVarExpr)
            ?: TODO())
    }

    private fun qsymAnalyser(form: QSymbolForm): ValueExpr {
        return (resolve(form.sym)?.let(::GlobalVarExpr)
            ?: TODO())
    }

    private fun ifAnalyser(it: AnalyserState): ValueExpr {
        val predExpr = exprAnalyser(it)
        val thenExpr = exprAnalyser(it)
        val elseExpr = exprAnalyser(it)
        it.expectEnd()
        return IfExpr(predExpr, thenExpr, elseExpr)
    }

    private fun letAnalyser(it: AnalyserState): ValueExpr {
        var ana = this
        return LetExpr(
            it.nested(VectorForm::forms) { bindingState ->
                bindingState.varargs {
                    val localVar = LocalVar(it.expectForm<SymbolForm>().sym)
                    val expr = ana.copy(loopLocals = null).exprAnalyser(it)

                    ana = ana.copy(locals = ana.locals.plus(localVar.sym to localVar))
                    LetBinding(localVar, expr)
                }
            },

            ana.exprAnalyser(it))
    }

    private fun loopAnalyser(it: AnalyserState): ValueExpr {
        val bindings = it.nested(VectorForm::forms) { bindingState ->
            val bindingCtx = this.copy(loopLocals = null)

            bindingState.varargs {
                LetBinding(LocalVar(it.expectForm<SymbolForm>().sym), bindingCtx.exprAnalyser(it))
            }
        }

        val ana = this.copy(locals = locals.plus(bindings.map { it.localVar.sym to it.localVar }), loopLocals = bindings.map { it.localVar })
        return LoopExpr(bindings, ana.exprAnalyser(it))
    }

    private fun recurAnalyser(it: AnalyserState): ValueExpr {
        if (loopLocals == null) TODO()

        val recurExprs = it.varargs(this::exprAnalyser)

        if (loopLocals.size != recurExprs.size) TODO()

        return RecurExpr(loopLocals.zip(recurExprs))
    }

    private fun fnAnalyser(it: AnalyserState): ValueExpr {
        val fnName: Symbol? = it.maybe { it.expectForm<SymbolForm>().sym }

        val paramNames = it.nested(VectorForm::forms) {
            it.varargs {
                it.expectForm<SymbolForm>().sym
            }
        }

        val newLocals = paramNames.map { it to LocalVar(it) }

        val ana = this.copy(locals = locals.plus(newLocals), loopLocals = newLocals.map { it.second })

        val bodyExpr = ana.doAnalyser(it)

        return FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr)
    }

    private fun callAnalyser(it: AnalyserState): ValueExpr =
        CallExpr(exprAnalyser(it), it.varargs(::exprAnalyser))

    private fun doAnalyser(it: AnalyserState): ValueExpr {
        val exprs = listOf(exprAnalyser(it)).plus(it.varargs(::exprAnalyser))
        return DoExpr(exprs.dropLast(1), exprs.last())
    }

    private fun caseAnalyser(it: AnalyserState): ValueExpr {
        val expr = exprAnalyser(it)

        val clauses = mutableListOf<CaseClause>()

        while (it.forms.size > 1) {
            val clauseForm = it.expectForm<Form>()

            fun resolveConstructor(form: Form): ConstructorVar {
                return when (form) {
                    is SymbolForm -> resolve(form.sym)
                    else -> TODO()
                } as? ConstructorVar ?: TODO()
            }

            val (constructor, paramSyms) = when (clauseForm) {
                is SymbolForm -> Pair(resolveConstructor(clauseForm).constructor, null)
                is ListForm -> {
                    it.nested(clauseForm.forms) {
                        Pair(resolveConstructor(it.expectForm()).constructor, it.varargs { it.expectForm<SymbolForm>().sym })

                    }
                }
                else -> TODO()
            }

            val localVars = paramSyms?.map { it to LocalVar(it) }

            clauses += CaseClause(
                constructor,
                localVars?.map { it.second },
                (if (localVars == null) this else copy(locals = locals + localVars))
                    .exprAnalyser(it))
        }

        val defaultExpr = if (it.forms.isNotEmpty()) exprAnalyser(it) else null

        it.expectEnd()

        return CaseExpr(expr, clauses.toList(), defaultExpr)
    }

    private fun listAnalyser(it: AnalyserState): ValueExpr {
        if (it.forms.isEmpty()) throw AnalyserError.ExpectedForm

        val firstForm = it.forms[0]

        return if (firstForm is SymbolForm) {
            when (firstForm.sym) {
                IF, FN, LET, DO, CASE, LOOP, RECUR -> it.forms = it.forms.drop(1)
            }

            when (firstForm.sym) {
                IF -> ifAnalyser(it)
                FN -> fnAnalyser(it)
                LET -> letAnalyser(it)
                DO -> doAnalyser(it)
                CASE -> caseAnalyser(it)
                LOOP -> loopAnalyser(it)
                RECUR -> recurAnalyser(it)
                else -> callAnalyser(it)
            }
        } else {
            callAnalyser(it)
        }
    }

    private fun collAnalyser(transform: (List<ValueExpr>) -> ValueExpr): FormsAnalyser<ValueExpr> = {
        transform(it.varargs(::exprAnalyser))
    }

    private fun recordAnalyser(form: RecordForm): ValueExpr {
        val entries = mutableListOf<RecordEntry>()

        val state = AnalyserState(form.forms)
        state.varargs {
            val form = it.expectForm<Form>()
            val attr: Attribute = (when (form) {
                is SymbolForm -> resolve(form.sym)
                else -> TODO()
            } as? AttributeVar)?.attribute ?: TODO()

            entries += RecordEntry(attr, exprAnalyser(it))
        }

        return RecordExpr(entries)
    }

    private fun exprAnalyser(it: AnalyserState): ValueExpr {
        val form = it.expectForm<Form>()

        return when (form) {
            is BooleanForm -> BooleanExpr(form.bool)
            is StringForm -> StringExpr(form.string)
            is IntForm -> IntExpr(form.int)
            is BigIntForm -> BigIntExpr(form.bigInt)
            is FloatForm -> FloatExpr(form.float)
            is BigFloatForm -> BigFloatExpr(form.bigFloat)

            is SymbolForm -> symAnalyser(form)
            is QSymbolForm -> qsymAnalyser(form)

            is ListForm -> listAnalyser(AnalyserState(form.forms))
            is VectorForm -> collAnalyser(::VectorExpr)(AnalyserState(form.forms))
            is SetForm -> collAnalyser(::SetExpr)(AnalyserState(form.forms))
            is RecordForm -> recordAnalyser(form)
            is QuoteForm -> TODO()
            is UnquoteForm -> TODO()
            is UnquoteSplicingForm -> TODO()
        }
    }

    fun analyseValueExpr(forms: List<Form>): ValueExpr = doAnalyser(AnalyserState(forms))
}

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

internal class TypeAnalyser(val env: Env, val nsEnv: NSEnv) {
    val tvMapping: MutableMap<Symbol, TypeVarType> = mutableMapOf()

    private fun tv(sym: Symbol): TypeVarType? =
        if (Character.isLowerCase(sym.baseStr.first())) {
            tvMapping.getOrPut(sym) { TypeVarType() }
        } else null

    fun monoTypeAnalyser(it: AnalyserState): MonoType {
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

            is VectorForm -> VectorType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is SetForm -> SetType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is ListForm -> it.nested(form.forms) { AppliedType(monoTypeAnalyser(it), it.varargs(::monoTypeAnalyser)) }

            else -> TODO()
        }
    }
}

