package brj

import brj.builtins.InstantiateFn
import brj.builtins.InvokeMemberFn
import brj.runtime.*
import brj.runtime.DefxVar
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.source.SourceSection

private val FX = "_fx".sym
internal val DEFAULT_FX_LOCAL = LocalVar(FX)

private val DO = "do".sym
private val IF = "if".sym
private val LET = "let".sym
private val FN = "fn".sym
private val WITH_FX = "with-fx".sym
private val LOOP = "loop".sym
private val RECUR = "recur".sym
private val NEW = "new".sym
private val CASE = "case".sym

private val DEF = "def".sym
private val DEFX = "defx".sym
private val IMPORT = "import".sym

internal sealed class TopLevelDoOrExpr

internal data class TopLevelDo(val forms: List<Form>) : TopLevelDoOrExpr()
internal data class TopLevelExpr(val expr: Expr) : TopLevelDoOrExpr()

private typealias LoopLocals = List<LocalVar>?

internal data class ExprAnalyser(
    private val env: BridjeContext,
    private val nsEnv: NsContext,
    private val locals: Map<Symbol, LocalVar> = emptyMap(),
    private val fxLocal: LocalVar = DEFAULT_FX_LOCAL
) {
    private fun Zip<Form>?.analyseImplicitDo(loopLocals: LoopLocals): ValueExpr {
        if (this == null) TODO("`do` requires at least one expr")

        val zips = zrights.toList()

        return DoExpr(
            zips.dropLast(1).map { it.analyseValueExpr(null) },
            zips.last().analyseValueExpr(loopLocals),
            zup!!.znode.loc
        )
    }

    private fun Zip<Form>.analyseDo(loopLocals: LoopLocals): ValueExpr = zright.analyseImplicitDo(loopLocals)

    private fun Zip<Form>.analyseIf(loopLocals: LoopLocals): ValueExpr {
        val predZip = zright ?: TODO("if missing predicate")
        val thenZip = predZip.zright ?: TODO("if missing then")
        val elseZip = thenZip.zright ?: TODO("if missing else")
        return IfExpr(
            predZip.analyseValueExpr(null),
            thenZip.analyseValueExpr(loopLocals),
            elseZip.analyseValueExpr(loopLocals),
            zup!!.znode.loc
        )
    }

    private fun Zip<Form>.analyseLet(loopLocals: LoopLocals): ValueExpr =
        (zright ?: TODO("expected binding form")).run {
            var analyser = this@ExprAnalyser

            if (znode !is VectorForm) TODO("expected vector")

            val bindings = sequence {
                var z = zdown
                while (z != null) {
                    val sym = ((z.znode as? SymbolForm) ?: TODO("expected symbol")).sym
                    val localVar = sym.lv
                    z = (z.zright ?: TODO("missing expr"))
                    val expr = analyser.run { z!!.analyseValueExpr(null) }
                    yield(Binding(localVar, expr))
                    analyser = analyser.copy(locals = analyser.locals + (sym to localVar))
                    z = z.zright
                }
            }.toList()

            return analyser.run {
                LetExpr(bindings, zright.analyseImplicitDo(loopLocals), zup!!.znode.loc)
            }
        }

    private fun Zip<Form>?.analyseFnBody(params: List<LocalVar>): FnExpr {
        if (this == null) TODO("missing fn body")
        return FnExpr(
            fxLocal, params,
            ExprAnalyser(env, nsEnv, params.associateBy(LocalVar::symbol)).run {
                analyseImplicitDo(loopLocals = params)
            },
            zup!!.znode.loc
        )
    }

    private fun Zip<Form>.analyseFn(): FnExpr {
        (zright ?: TODO("expected form")).run {
            if (znode !is VectorForm) TODO("expected vector")
            val params = zdown.zrights.map {
                ((it.znode as? SymbolForm) ?: TODO("expected symbol")).sym.lv
            }.toList()

            return zright.analyseFnBody(params)
        }
    }

    private fun Zip<Form>.analyseWithFx(loopLocals: LoopLocals): ValueExpr =
        (zright ?: TODO("expected form")).run {
            if (znode !is VectorForm) TODO("expected vector")

            val bindings = zdown.zrights.map {
                if (it.znode !is ListForm) TODO("expected def")
                val defExpr = it.zdown.run {
                    if (this == null || (znode as? SymbolForm)?.sym != DEF) TODO("expected def")
                    analyseDef()
                }
                WithFxBinding(
                    (resolveGlobalVar(defExpr.sym) as? DefxVar) ?: TODO("unknown var ${defExpr.sym}"),
                    defExpr.expr
                )
            }.toList()

            val newFx = FX.lv

            val expr = this@ExprAnalyser.copy(fxLocal = newFx).run {
                zright.analyseImplicitDo(loopLocals)
            }

            WithFxExpr(fxLocal, bindings, newFx, expr, zup!!.znode.loc)
        }

    private fun Zip<Form>.analyseLoop(): ValueExpr =
        (zright ?: TODO("expected form")).run {
            if (znode !is VectorForm) TODO("expected vector")
            val bindings = sequence {
                var z = zdown
                while (z != null) {
                    val localVar = ((z.znode as? SymbolForm) ?: TODO("expected symbol")).sym.lv
                    z = (z.zright ?: TODO("missing expr"))
                    val expr = z.analyseValueExpr(null)
                    yield(Binding(localVar, expr))
                    z = z.zright
                }
            }.toList()

            val analyser = this@ExprAnalyser.copy(locals = locals + bindings.map { it.binding.symbol to it.binding })

            return analyser.run {
                LoopExpr(bindings, zright.analyseImplicitDo(bindings.map { it.binding }), zup!!.znode.loc)
            }
        }

    private fun Zip<Form>.analyseRecur(loopLocals: LoopLocals): ValueExpr {
        if (loopLocals == null) TODO("recur from non-tail position")

        val rights = zright.zrights.toList()

        if (rights.size != loopLocals.size) TODO("mismatch in recur var count")

        val exprs = rights.map { it.analyseValueExpr(null) }

        return RecurExpr(loopLocals.zip(exprs).map { Binding(it.first, it.second) }, zup!!.znode.loc)
    }

    private fun Zip<Form>.analyseRecord() =
        RecordExpr(
            sequence {
                var z = zdown
                while (z != null) {
                    val k = (((z.znode as? KeywordForm) ?: TODO("expecting keyword")).sym)
                    z = z.zright ?: TODO("expecting value")
                    yield(k to z.analyseValueExpr(null))
                    z = z.zright
                }
            }.toMap(),
            znode.loc
        )

    private fun Zip<Form>.analyseNew() =
        (zright ?: TODO("expected form")).run {
            NewExpr(
                analyseValueExpr(null),
                sequence {
                    var z = zright
                    while (z != null) {
                        this.yield(z.analyseValueExpr(null))
                        z = z.zright
                    }
                }.toList(),
                zup!!.znode.loc
            )
        }


    private fun Zip<Form>.analyseCase(loopLocals: LoopLocals): ValueExpr =
        (zright ?: TODO("expected expr in `case`")).run {
            val expr = analyseValueExpr(null)

            var nilClause: ValueExpr? = null
            val clauses = mutableListOf<CaseClause>()
            var default: ValueExpr? = null

            var z: Zip<Form>? = zright ?: TODO("case without any forms")

            while (z != null) {
                val zafter = z.zright
                if (zafter == null) {
                    default = z.analyseValueExpr(loopLocals)
                    z = null
                } else {
                    z.run {
                        znode.run {
                            when (this) {
                                is NilForm -> {
                                    if (nilClause != null) TODO("duplicate `nil` clauses in case")
                                    nilClause = zafter.analyseValueExpr(loopLocals)
                                }

                                is ListForm -> {
                                    val metaObjectZip = zdown ?: TODO("empty list")
                                    val bindingZip = metaObjectZip.zright ?: TODO("missing binding")
                                    val bindingSym = (bindingZip.znode as? SymbolForm)?.sym?.takeIf { it.ns == null }
                                        ?: TODO("expected symbol")
                                    val localVar = bindingSym.lv

                                    clauses += CaseClause(
                                        BridjeKey(
                                            ((metaObjectZip.znode as? KeywordForm) ?: TODO("expected keyword")).sym
                                        ),
                                        localVar,
                                        this@ExprAnalyser.copy(locals = locals + (bindingSym to localVar)).run {
                                            zafter.analyseValueExpr(loopLocals)
                                        }
                                    )
                                }

                                else -> TODO("unexpected pattern in `case`")
                            }
                        }
                    }

                    z = zafter.zright
                }
            }

            return CaseExpr(expr, nilClause, clauses, default, zup!!.znode.loc)
        }

    private fun resolveGlobalVar(sym: Symbol) =
        nsEnv.globalVars[sym] ?: nsEnv.refers[sym] ?: env.coreNsContext.globalVars[sym]

    private fun resolveHostSymbol(sym: Symbol, loc: SourceSection?): TruffleObject? {
        if (sym.ns == null) {
            env.imports[sym]?.let { return it }

            runCatching { env.truffleEnv.lookupHostSymbol(sym.local) }
                .getOrNull()
                ?.let { return it as TruffleObject }
        } else {
            env.imports[sym.ns]?.let { clazz ->
                if (env.interop.isMemberReadable(clazz, sym.local)) {
                    return env.interop.readMember(clazz, sym.local) as TruffleObject
                } else TODO("unknown static method '$sym', $loc")
            }
        }

        return null
    }

    private fun Zip<Form>.analyseValueExpr(loopLocals: LoopLocals): ValueExpr = znode.run {
        when (this) {
            is NilForm -> NilExpr(loc)
            is IntForm -> IntExpr(int, loc)
            is BoolForm -> BoolExpr(bool, loc)
            is StringForm -> StringExpr(string, loc)
            is VectorForm -> VectorExpr(zchildren.map { it.analyseValueExpr(null) }, loc)
            is SetForm -> SetExpr(zchildren.map { it.analyseValueExpr(null) }, loc)
            is RecordForm -> analyseRecord()

            is SymbolForm -> {
                if (sym.ns == null) {
                    locals[sym]?.let { return LocalVarExpr(it, loc) }
                    resolveGlobalVar(sym)?.let { return GlobalVarExpr(it, loc) }
                }

                resolveHostSymbol(sym, loc)?.let { return TruffleObjectExpr(it, loc) }

                TODO("can't find symbol: $sym")
            }

            is KeywordForm -> KeywordExpr(BridjeKey(sym), loc)
            is KeywordDotForm -> TruffleObjectExpr(InstantiateFn(BridjeKey(sym)), loc)

            is DotSymbolForm -> TruffleObjectExpr(InvokeMemberFn(sym), loc)
            is SymbolDotForm -> TruffleObjectExpr(
                InstantiateFn(
                    resolveHostSymbol(sym, loc) ?: TODO("can't find host symbol '$sym'")
                ),
                loc
            )

            is ListForm -> {
                zdown.run {
                    if (this == null) TODO("empty list")

                    znode.run {
                        val specialFormExpr = when (this) {
                            is SymbolForm -> when (sym) {
                                DO -> analyseDo(loopLocals)
                                IF -> analyseIf(loopLocals)
                                LET -> analyseLet(loopLocals)
                                FN -> analyseFn()
                                WITH_FX -> analyseWithFx(loopLocals)
                                LOOP -> analyseLoop()
                                RECUR -> analyseRecur(loopLocals)
                                NEW -> analyseNew()
                                CASE -> analyseCase(loopLocals)
                                else -> null
                            }

                            else -> null
                        }

                        if (specialFormExpr != null)
                            specialFormExpr
                        else {
                            CallExpr(
                                analyseValueExpr(null),
                                LocalVarExpr(fxLocal, null),
                                zright.zrights.map { it.analyseValueExpr(null) }.toList(),
                                zup!!.znode.loc
                            )
                        }
                    }
                }
            }
        }
    }

    private fun Zip<Form>.analyseMonoType(): MonoType = znode.run {
        when (this) {
            is IntForm, is BoolForm, is StringForm -> TODO("invalid type")
            is RecordForm -> TODO()

            is SymbolForm -> when (sym) {
                "Int".sym -> IntType
                "Str".sym -> StringType
                "Bool".sym -> BoolType
                else -> TODO()
            }

            is ListForm -> zdown.run {
                if (this == null) TODO("expected form")
                znode.run {
                    when (((this as? SymbolForm) ?: TODO("expected symbol")).sym) {
                        "Fn".sym -> {
                            val paramsZip = zright ?: TODO("expected params")
                            val params = paramsZip.run {
                                if (znode !is ListForm) TODO("expected param list")
                                zdown.zrights.map { it.analyseMonoType() }.toList()
                            }
                            val resultZip = (zright ?: TODO("expected result type"))
                            val result = resultZip.analyseMonoType()
                            if (resultZip.zright != null) TODO("extra form")
                            FnType(params, result)
                        }

                        else -> TODO("unexpected sym")
                    }
                }
            }

            is VectorForm -> zdown.run {
                if (this == null) TODO("expected form")
                if (zright != null) TODO("too many forms")
                VectorType(analyseMonoType())
            }

            is SetForm -> zdown.run {
                if (this == null) TODO("expected form")
                if (zright != null) TODO("too many forms")
                SetType(analyseMonoType())
            }

            else -> TODO()
        }
    }


    data class DefHeader(val sym: Symbol, val params: Sequence<Zip<Form>>?)

    private fun Zip<Form>?.analyseDefHeader(): DefHeader {
        if (this == null) TODO("expected sym after `def`")

        return znode.run {
            when (this) {
                is SymbolForm -> DefHeader(sym, null)
                is ListForm -> zdown.run {
                    if (this == null) TODO("empty list in `def`")
                    znode.run {
                        val sym = ((this as? SymbolForm) ?: TODO("expected sym after `def`")).sym

                        DefHeader(sym, zright.zrights)
                    }
                }

                else -> TODO("unexpected form in `def`")
            }
        }
    }

    private fun Zip<Form>.analyseDef(): DefExpr {
        val header = zright.analyseDefHeader()
        val params =
            header.params?.map { ((it.znode as? SymbolForm) ?: TODO("expected symbol in def")).sym.lv }?.toList()

        val expr = (zright?.zright ?: TODO("expected def body")).run {
            if (params != null) analyseFnBody(params) else analyseImplicitDo(null)
        }

        return DefExpr(header.sym, expr, zup!!.znode.loc)
    }

    private fun Zip<Form>.analyseDefx(): DefxExpr {
        val header = analyseDefHeader()
        val resultTypeZip = zright ?: TODO("missing result type")
        val monoType =
            if (header.params != null)
                FnType(
                    header.params.map { it.analyseMonoType() }.toList(),
                    resultTypeZip.analyseMonoType()
                )
            else
                resultTypeZip.analyseMonoType() as? FnType ?: TODO("failed to parse MonoType")

        return DefxExpr(header.sym, Typing(monoType, fx = setOf(header.sym)), zup!!.znode.loc)
    }

    private fun Zip<Form>.parseImport() =
        ImportExpr(
            zright.zrights.map { ((it.znode as? SymbolForm) ?: TODO("non-symbol in import")).sym }.toList(),
            zup!!.znode.loc
        )

    fun Zip<Form>.analyseExpr(): TopLevelDoOrExpr = znode.run {
        when (this) {
            is ListForm -> zdown?.run {
                when ((znode as? SymbolForm)?.sym) {
                    DO -> TopLevelDo(zright.zrights.map { it.znode }.toList())
                    DEF -> TopLevelExpr(analyseDef())
                    DEFX -> TopLevelExpr(analyseDefx())
                    IMPORT -> TopLevelExpr(parseImport())
                    else -> null
                }
            }

            else -> null
        } ?: TopLevelExpr(analyseValueExpr(null))
    }

    fun analyseExpr(form: Form) = form.zip.analyseExpr()
}

