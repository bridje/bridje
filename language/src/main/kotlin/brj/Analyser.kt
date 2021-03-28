package brj

import brj.runtime.BridjeEnv
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.source.SourceSection

internal val fxLocal = LocalVar(symbol("_fx"))

private val DO = symbol("do")
private val IF = symbol("if")
private val LET = symbol("let")
private val FN = symbol("fn")
private val DEF = symbol("def")
private val DEFX = symbol("defx")

internal sealed class DoOrExpr

internal data class TopLevelDo(val forms: List<Form>) : DoOrExpr()
internal data class TopLevelExpr(val expr: Expr) : DoOrExpr()

internal class Analyser(private val env: BridjeEnv, private val locals: Map<Symbol, LocalVar> = emptyMap()) {

    private fun FormParser.parseValueExpr() = analyseValueExpr(expectForm())

    private fun FormParser.parseMonoType() = analyseMonoType(expectForm())

    private fun parseDo(formParser: FormParser, loc: SourceSection? = null) = formParser.run {
        val exprs = rest { analyseValueExpr(expectForm()) }
        if (exprs.isEmpty()) TODO()
        DoExpr(exprs.dropLast(1), exprs.last(), loc)
    }

    private fun parseIf(formParser: FormParser, loc: SourceSection?) = formParser.run {
        val expr = IfExpr(parseValueExpr(), parseValueExpr(), parseValueExpr(), loc)
        expectEnd()
        expr
    }

    private fun parseLet(formParser: FormParser, loc: SourceSection?): ValueExpr {
        var analyser = this

        val bindingForm = formParser.expectForm(VectorForm::class.java)
        val bindings = parseForms(bindingForm.forms) {
            rest {
                val sym = expectSymbol()
                val localVar = LocalVar(sym)
                val binding = LetBinding(localVar, analyser.analyseValueExpr(expectForm()))
                analyser = Analyser(env, analyser.locals + (sym to localVar))
                binding
            }
        }

        return LetExpr(bindings, Analyser(env, analyser.locals).parseDo(formParser), loc)
    }

    private fun parseFn(formParser: FormParser, loc: SourceSection?) = formParser.run {
        val params = FormParser(expectForm(VectorForm::class.java).forms).rest { LocalVar(expectSymbol()) }
        FnExpr(listOf(fxLocal) + params, Analyser(env, params.associateBy(LocalVar::symbol)).parseDo(formParser), loc)
    }

    private fun analyseValueExpr(form: Form): ValueExpr = when (form) {
        is ListForm -> parseForms(form.forms) {
            or({
                when (maybe { expectSymbol() }) {
                    DO -> parseDo(this, form.loc)
                    IF -> parseIf(this, form.loc)
                    LET -> parseLet(this, form.loc)
                    FN -> parseFn(this, form.loc)
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
            val elType = parseMonoType()
            expectEnd()
            VectorType(elType)
        }

        is SetForm -> parseForms(form.forms) {
            val elType = parseMonoType()
            expectEnd()
            SetType(elType)
        }
    }

    private fun FormParser.parseDef(loc: SourceSection?): Expr =
        DefExpr(expectSymbol(), parseValueExpr(), loc)
            .also { expectEnd() }

    private fun FormParser.parseDefx(loc: SourceSection?): Expr {
        val sym = expectSymbol()
        val typing = Typing(parseMonoType(), fx = setOf(sym))
        expectEnd()

        return DefxExpr(sym, typing, loc)
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
                            DEF -> TopLevelExpr(parseDef(listForm.loc))
                            DEFX -> TopLevelExpr(parseDefx(listForm.loc))
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
