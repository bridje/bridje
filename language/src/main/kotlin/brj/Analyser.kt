package brj

import brj.builtins.InstantiateFn
import brj.builtins.InvokeMemberFn
import brj.runtime.BridjeContext
import brj.runtime.BridjeKey
import brj.runtime.DefxVar
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.source.SourceSection

private val FX = symbol("_fx")
internal val DEFAULT_FX_LOCAL = LocalVar(FX)

private val DO = symbol("do")
private val IF = symbol("if")
private val LET = symbol("let")
private val FN = symbol("fn")
private val WITH_FX = symbol("with-fx")
private val LOOP = symbol("loop")
private val RECUR = symbol("recur")
private val NEW = symbol("new")
private val CASE = symbol("case")

private val DEF = symbol("def")
private val DEFX = symbol("defx")
private val IMPORT = symbol("import")

internal sealed class TopLevelDoOrExpr

internal data class TopLevelDo(val forms: List<Form>) : TopLevelDoOrExpr()
internal data class TopLevelExpr(val expr: Expr) : TopLevelDoOrExpr()

private typealias LoopLocals = List<LocalVar>?

internal data class Analyser(
    private val env: BridjeContext,
    private val locals: Map<Symbol, LocalVar> = emptyMap(),
    private val fxLocal: LocalVar = DEFAULT_FX_LOCAL
) {

    private fun parseDo(formParser: FormParser, loopLocals: LoopLocals, loc: SourceSection? = null) = formParser.run {
        val exprs = rest { analyseValueExpr(expectForm(), if (forms.isNotEmpty()) null else loopLocals) }
        if (exprs.isEmpty()) TODO()
        DoExpr(exprs.dropLast(1), exprs.last(), loc)
    }

    private fun parseIf(formParser: FormParser, loopLocals: LoopLocals, loc: SourceSection?) = formParser.run {
        IfExpr(
            analyseValueExpr(expectForm(), null),
            analyseValueExpr(expectForm(), loopLocals),
            analyseValueExpr(expectForm(), loopLocals),
            loc
        ).also { expectEnd() }
    }

    private fun parseLet(formParser: FormParser, loopLocals: LoopLocals, loc: SourceSection?): ValueExpr =
        formParser.run {
            var analyser = this@Analyser

            val bindingForm = expectForm(VectorForm::class.java)
            val bindings = parseForms(bindingForm.forms) {
                rest {
                    val sym = expectSymbol()
                    val localVar = LocalVar(sym)
                    val binding = Binding(localVar, analyser.analyseValueExpr(expectForm(), loopLocals))
                    analyser = analyser.copy(locals = analyser.locals + (sym to localVar))
                    binding
                }.also { expectEnd() }
            }

            return LetExpr(bindings, analyser.parseDo(this, loopLocals), loc)
                .also { expectEnd() }
        }

    private fun parseFn(params: List<LocalVar>, formParser: FormParser, loc: SourceSection?) =
        FnExpr(
            fxLocal, params,
            Analyser(env, params.associateBy(LocalVar::symbol)).parseDo(formParser, loopLocals = params), loc
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

    private fun parseWithFx(formParser: FormParser, loopLocals: LoopLocals, loc: SourceSection?): ValueExpr =
        formParser.run {
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
                this@Analyser.copy(fxLocal = newFx).analyseValueExpr(expectForm(), loopLocals),
                loc
            ).also { expectEnd() }
        }

    private fun parseLoop(formParser: FormParser, loc: SourceSection?): ValueExpr =
        formParser.run {
            val bindings = parseForms(expectForm(VectorForm::class.java).forms) {
                rest {
                    Binding(
                        LocalVar(expectSymbol()),
                        analyseValueExpr(expectForm(), loopLocals = null)
                    )
                }.also { expectEnd() }
            }

            val analyser = copy(locals = locals + bindings.map { it.binding.symbol to it.binding })

            LoopExpr(bindings, analyser.parseDo(this, bindings.map { it.binding }), loc)
                .also { expectEnd() }
        }

    private fun parseRecur(formParser: FormParser, loopLocals: LoopLocals, loc: SourceSection?): ValueExpr {
        if (loopLocals == null) TODO()

        if (formParser.forms.size != loopLocals.size) TODO()

        val exprs = formParser.rest { analyseValueExpr(expectForm(), loopLocals = null) }

        return RecurExpr(loopLocals.zip(exprs).map { Binding(it.first, it.second) }, loc)
    }

    private fun parseRecord(formParser: FormParser, loc: SourceSection?): ValueExpr = formParser.run {
        RecordExpr(
            rest {
                expectKeyword() to analyseValueExpr(expectForm(), loopLocals = null)
            }.toMap(),
            loc
        ).also { expectEnd() }
    }

    private fun parseNew(formParser: FormParser, loc: SourceSection?): ValueExpr = formParser.run {
        NewExpr(analyseValueExpr(expectForm(), null), rest { analyseValueExpr(expectForm(), null) }, loc)
    }

    private fun parseCase(formParser: FormParser, loopLocals: LoopLocals, loc: SourceSection?): ValueExpr =
        formParser.run {
            val expr = analyseValueExpr(expectForm(), null)

            var nilClause: ValueExpr? = null
            val clauses = mutableListOf<CaseClause>()
            var default: ValueExpr? = null

            if (forms.isEmpty()) TODO()

            rest {
                val form = expectForm()
                when {
                    forms.isEmpty() -> default = analyseValueExpr(form, loopLocals)
                    form is NilForm -> {
                        if (nilClause != null) TODO()
                        nilClause = analyseValueExpr(expectForm(), loopLocals)
                    }

                    form is ListForm -> {
                        val (keySym, binding) = parseForms(form.forms) {
                            Pair(expectKeyword(), expectSymbol().also { if (it.ns != null) TODO() })
                                .also { expectEnd() }
                        }
                        val localVar = LocalVar(binding)
                        val analyser = copy(locals = locals + (binding to localVar))
                        val clauseExpr = analyser.analyseValueExpr(expectForm(), loopLocals)
                        clauses += CaseClause(BridjeKey(keySym), localVar, clauseExpr)
                    }

                    else -> TODO()
                }
            }

            CaseExpr(expr, nilClause, clauses, default, loc)
        }

    private fun resolveHostSymbol(sym: Symbol): TruffleObject? {
        if (sym.ns == null) {
            env.imports[sym]?.let { return it }

            runCatching { env.truffleEnv.lookupHostSymbol(sym.local) }
                .getOrNull()
                ?.let { return it as TruffleObject }
        } else {
            env.imports[sym.ns]?.let { clazz ->
                if (env.interop.isMemberReadable(clazz, sym.local)) {
                    return env.interop.readMember(clazz, sym.local) as TruffleObject
                } else TODO()
            }
        }

        return null
    }

    private fun analyseValueExpr(form: Form, loopLocals: LoopLocals): ValueExpr = when (form) {
        is ListForm -> parseForms(form.forms) {
            or({
                when (maybe { expectSymbol() }) {
                    DO -> parseDo(this, loopLocals, form.loc)
                    IF -> parseIf(this, loopLocals, form.loc)
                    LET -> parseLet(this, loopLocals, form.loc)
                    FN -> parseFn(this, form.loc)
                    WITH_FX -> parseWithFx(this, loopLocals, form.loc)
                    LOOP -> parseLoop(this, form.loc)
                    RECUR -> parseRecur(this, loopLocals, form.loc)
                    NEW -> parseNew(this, form.loc)
                    CASE -> parseCase(this, loopLocals, form.loc)
                    else -> null
                }
            }, {
                CallExpr(
                    analyseValueExpr(expectForm(), loopLocals),
                    LocalVarExpr(fxLocal, null),
                    rest { analyseValueExpr(expectForm(), loopLocals) },
                    form.loc
                )
            }) ?: TODO()
        }

        is NilForm -> NilExpr(form.loc)
        is IntForm -> IntExpr(form.int, form.loc)
        is BoolForm -> BoolExpr(form.bool, form.loc)
        is StringForm -> StringExpr(form.string, form.loc)
        is VectorForm -> VectorExpr(form.forms.map { analyseValueExpr(it, loopLocals) }, form.loc)
        is SetForm -> SetExpr(form.forms.map { analyseValueExpr(it, loopLocals) }, form.loc)
        is RecordForm -> parseRecord(FormParser(form.forms), form.loc)

        is SymbolForm -> {
            val sym = form.sym

            if (sym.ns == null) {
                locals[sym]?.let { return LocalVarExpr(it, form.loc) }
                env.globalVars[sym]?.let { return GlobalVarExpr(it, form.loc) }
            }

            resolveHostSymbol(sym)?.let { return TruffleObjectExpr(it, form.loc) }

            TODO("can't find symbol: $sym")
        }

        is DotSymbolForm -> TruffleObjectExpr(InvokeMemberFn(form.sym), form.loc)
        is SymbolDotForm -> TruffleObjectExpr(InstantiateFn(resolveHostSymbol(form.sym) ?: TODO()), form.loc)
        is KeywordForm -> KeywordExpr(BridjeKey(form.sym), form.loc)
        is KeywordDotForm -> TruffleObjectExpr(InstantiateFn(BridjeKey(form.sym)), form.loc)
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
            when (expectSymbol()) {
                symbol("Fn") -> {
                    FnType(
                        parseForms(expectForm(ListForm::class.java).forms) {
                            rest { analyseMonoType(expectForm()) }
                        },
                        analyseMonoType(expectForm())
                    )
                }
                else -> TODO()
            }
        }

        is VectorForm -> parseForms(form.forms) {
            VectorType(analyseMonoType(expectForm())).also { expectEnd() }
        }

        is SetForm -> parseForms(form.forms) {
            SetType(analyseMonoType(expectForm())).also { expectEnd() }
        }

        else -> TODO()
    }

    data class DefHeader(val sym: Symbol, val paramForms: List<Form>?)

    private fun parseDefHeader(formParser: FormParser) = formParser.run {
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
        val header = parseDefHeader(this) ?: TODO()
        val expr =
            if (header.paramForms != null)
                parseFn(parseForms(header.paramForms) { rest { LocalVar(expectSymbol()) } }, this, loc)
            else
                analyseValueExpr(expectForm(), null)

        DefExpr(header.sym, expr, loc)
    }

    private fun parseDefx(formParser: FormParser, loc: SourceSection?) = formParser.run {
        val header = parseDefHeader(formParser) ?: TODO()
        val monoType =
            if (header.paramForms != null)
                FnType(
                    parseForms(header.paramForms) { rest { analyseMonoType(expectForm()) } },
                    analyseMonoType(expectForm())
                )
            else
                analyseMonoType(expectForm()) as? FnType ?: TODO()

        DefxExpr(header.sym, Typing(monoType, fx = setOf(header.sym)), loc)
            .also { expectEnd() }
    }

    private fun parseImport(formParser: FormParser, loc: SourceSection?) = formParser.run {
        ImportExpr(rest { expectSymbol() }, loc).also { expectEnd() }
    }

    fun analyseExpr(form: Form): TopLevelDoOrExpr = parseForms(listOf(form)) {
        or({
            maybe { expectForm(ListForm::class.java) }?.let { listForm ->
                parseForms(listForm.forms) {
                    maybe {
                        expectSymbol().takeIf { it == DEF || it == DEFX || it == DO || it == IMPORT }
                    }?.let { sym ->
                        when (sym) {
                            DO -> TopLevelDo(forms)
                            DEF -> TopLevelExpr(parseDef(this, listForm.loc))
                            DEFX -> TopLevelExpr(parseDefx(this, listForm.loc))
                            IMPORT -> TopLevelExpr(parseImport(this, listForm.loc))
                            else -> null
                        }
                    }
                }
            }
        }, {
            TopLevelExpr(analyseValueExpr(expectForm(), null))
        })
    } ?: TODO()
}
