package brj

import brj.runtime.BridjeEnv
import brj.runtime.DefxVar
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.source.SourceSection

private val FX = symbol("_fx")
internal val DEFAULT_FX_LOCAL = LocalVar(FX)

private val DO = symbol("do")
private val IF = symbol("if")
private val LET = symbol("let")
private val FN = symbol("fn")
private val DEF = symbol("def")
private val DEFX = symbol("defx")
private val WITH_FX = symbol("with-fx")

internal sealed class DoOrExpr

internal data class TopLevelDo(val forms: List<Form>) : DoOrExpr()
internal data class TopLevelExpr(val expr: Expr) : DoOrExpr()

internal data class Analyser(
    private val env: BridjeEnv,
    private val locals: Map<Symbol, LocalVar> = emptyMap(),
    private val fxLocal: LocalVar = DEFAULT_FX_LOCAL
) {

    private fun FormParser.parseValueExpr() = analyseValueExpr(expectForm())

    private fun FormParser.parseMonoType() = analyseMonoType(expectForm())

    private fun parseDo(formParser: FormParser, loc: SourceSection? = null) = formParser.run {
        val exprs = rest { parseValueExpr() }
        if (exprs.isEmpty()) TODO()
        DoExpr(exprs.dropLast(1), exprs.last(), loc)
    }

    private fun parseIf(formParser: FormParser, loc: SourceSection?) = formParser.run {
        IfExpr(parseValueExpr(), parseValueExpr(), parseValueExpr(), loc)
            .also { expectEnd() }
    }

    private fun parseLet(formParser: FormParser, loc: SourceSection?): ValueExpr = formParser.run {
        var analyser = this@Analyser

        val bindingForm = expectForm(VectorForm::class.java)
        val bindings = parseForms(bindingForm.forms) {
            rest {
                val sym = expectSymbol()
                val localVar = LocalVar(sym)
                val binding = LetBinding(localVar, analyser.analyseValueExpr(expectForm()))
                analyser = Analyser(env, analyser.locals + (sym to localVar))
                binding
            }.also { expectEnd() }
        }

        return LetExpr(bindings, Analyser(env, analyser.locals).parseDo(this), loc)
            .also { expectEnd() }
    }

    private fun parseFn(params: List<LocalVar>, formParser: FormParser, loc: SourceSection?) =
        FnExpr(
            listOf(fxLocal) + params,
            Analyser(env, params.associateBy(LocalVar::symbol)).parseDo(formParser), loc
        )

    private fun parseFn(formParser: FormParser, loc: SourceSection?) = formParser.run {
        parseFn(
            parseForms(expectForm(VectorForm::class.java).forms) {
                rest { LocalVar(expectSymbol()) }
            },
            this,
            loc
        )
    }

    private fun parseWithFx(formParser: FormParser, loc: SourceSection?): ValueExpr = formParser.run {
        val bindings = parseForms(expectForm(VectorForm::class.java).forms) {
            rest {
                val listForm = expectForm(ListForm::class.java)
                parseForms(listForm.forms) {
                    val defExpr = maybe {
                        if (expectSymbol() != DEF) TODO()
                        parseDef(this, listForm.loc).also { expectEnd() }
                    } ?: TODO()
                    WithFxBinding(env.globalVars[defExpr.sym] as? DefxVar ?: TODO(), defExpr.expr)
                }
            }
        }

        val newFx = LocalVar(FX)

        return WithFxExpr(
            fxLocal,
            bindings,
            newFx,
            this@Analyser.copy(fxLocal = newFx).analyseValueExpr(expectForm()),
            loc
        ).also { expectEnd() }
    }

    private fun analyseValueExpr(form: Form): ValueExpr = when (form) {
        is ListForm -> parseForms(form.forms) {
            or({
                when (maybe { expectSymbol() }) {
                    DO -> parseDo(this, form.loc)
                    IF -> parseIf(this, form.loc)
                    LET -> parseLet(this, form.loc)
                    FN -> parseFn(this, form.loc)
                    WITH_FX -> parseWithFx(this, form.loc)
                    else -> null
                }
            }, {
                CallExpr(parseValueExpr(), listOf(LocalVarExpr(fxLocal, null)) + rest { parseValueExpr() }, form.loc)
            }) ?: TODO()
        }

        is IntForm -> IntExpr(form.int, form.loc)
        is BoolForm -> BoolExpr(form.bool, form.loc)
        is StringForm -> StringExpr(form.string, form.loc)
        is VectorForm -> VectorExpr(form.forms.map { analyseValueExpr(it) }, form.loc)
        is SetForm -> SetExpr(form.forms.map { analyseValueExpr(it) }, form.loc)
        is RecordForm -> TODO()
        is SymbolForm -> {
            val sym = form.sym

            locals[sym]?.let { return LocalVarExpr(it, form.loc) }
            env.globalVars[sym]?.let { return GlobalVarExpr(it, form.loc) }
            TODO()
        }
    }

    private fun analyseMonoType(form: Form): MonoType = when (form) {
        is IntForm, is BoolForm, is StringForm -> TODO("invalid type")
        is RecordForm -> TODO()

        is SymbolForm -> when (form.sym) {
            symbol("Int") -> IntType
            symbol("Str") -> StringType
            symbol("Bool") -> BoolType
            else -> TODO()
        }

        is ListForm -> parseForms(form.forms) {
            when (val sym = expectSymbol()) {
                symbol("Fn") -> {
                    FnType(
                        parseForms(expectForm(ListForm::class.java).forms) {
                            rest { parseMonoType() }
                        },
                        parseMonoType()
                    )
                }
                else -> TODO()
            }
        }

        is VectorForm -> parseForms(form.forms) {
            VectorType(parseMonoType()).also { expectEnd() }
        }

        is SetForm -> parseForms(form.forms) {
            SetType(parseMonoType()).also { expectEnd() }
        }
    }

    data class DefHeader(val sym: Symbol, val paramForms: List<Form>?)

    private fun parseDefHeader(formParser: FormParser, loc: SourceSection?) = formParser.run {
        or({
            maybe { expectSymbol() }?.let { sym ->
                DefHeader(sym, null)
            }
        }, {
            maybe { expectForm(ListForm::class.java) }?.let { listForm ->
                parseForms(listForm.forms) {
                    DefHeader(expectSymbol(), rest { expectForm() }).also { expectEnd() }
                }
            }
        })
    }

    private fun parseDef(formParser: FormParser, loc: SourceSection?) = formParser.run {
        val header = parseDefHeader(this, loc) ?: TODO()
        val expr =
            if (header.paramForms != null)
                parseFn(parseForms(header.paramForms) { rest { LocalVar(expectSymbol()) } }, this, loc)
            else
                parseValueExpr()

        DefExpr(header.sym, expr, loc)
    }

    private fun parseDefx(formParser: FormParser, loc: SourceSection?) = formParser.run {
        val header = parseDefHeader(formParser, loc) ?: TODO()
        val monoType =
            if (header.paramForms != null)
                FnType(parseForms(header.paramForms) { rest { parseMonoType() } }, parseMonoType())
            else
                parseMonoType() as? FnType ?: TODO()

        DefxExpr(header.sym, Typing(monoType, fx = setOf(header.sym)), loc)
            .also { expectEnd() }
    }

    fun analyseExpr(form: Form): DoOrExpr = parseForms(listOf(form)) {
        or({
            maybe { expectForm(ListForm::class.java) }?.let { listForm ->
                parseForms(listForm.forms) {
                    maybe {
                        expectSymbol().takeIf { it == DEF || it == DEFX || it == DO }
                    }?.let { sym ->
                        when (sym) {
                            DO -> TopLevelDo(forms)
                            DEF -> TopLevelExpr(parseDef(this, listForm.loc))
                            DEFX -> TopLevelExpr(parseDefx(this, listForm.loc))
                            else -> null
                        }
                    }
                }
            }
        }, {
            TopLevelExpr(analyseValueExpr(expectForm()))
        })
    } ?: TODO()
}
