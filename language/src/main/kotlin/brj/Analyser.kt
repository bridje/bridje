package brj

import brj.runtime.BridjeEnv
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.source.SourceSection

internal class Analyser(private val env: BridjeEnv, private val locals: Map<Symbol, LocalVar> = emptyMap()) {

    private fun FormParser.parseValueExpr() = analyseValueExpr(expectForm())

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
        FnExpr(params, Analyser(env, params.associateBy(LocalVar::symbol)).parseDo(formParser), loc)
    }

    private fun analyseValueExpr(form: Form): ValueExpr = when (form) {
        is ListForm -> parseForms(form.forms) {
            or({
                when (expectSymbol()) {
                    DO -> parseDo(this, form.loc)
                    IF -> parseIf(this, form.loc)
                    LET -> parseLet(this, form.loc)
                    FN -> parseFn(this, form.loc)
                    else -> null
                }
            }, {
                CallExpr(parseValueExpr(), rest { parseValueExpr() }, form.loc)
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
            locals[sym]?.let {
                return LocalVarExpr(it, form.loc)
            }
            env.globalVars[sym]?.let {
                return GlobalVarExpr(it, form.loc)
            }
            TODO()
        }
    }

    private fun parseDef(formParser: FormParser, loc: SourceSection?): Expr = formParser.run {
        DefExpr(expectSymbol(), parseValueExpr(), loc)
            .also { expectEnd() }
    }

    fun analyseExpr(form: Form): Expr {
        return FormParser(listOf(form)).run {
            or({
                maybe {
                    val listForm = expectForm(ListForm::class.java)
                    FormParser(listForm.forms).maybe {
                        when (expectSymbol()) {
                            DEF -> parseDef(this, listForm.loc)
                            else -> null
                        }
                    }
                }
            }, {
                analyseValueExpr(expectForm())
            })
        } ?: TODO()
    }

    companion object {
        private val DO = symbol("do")
        private val IF = symbol("if")
        private val LET = symbol("let")
        private val FN = symbol("fn")
        private val DEF = symbol("def")
    }
}
