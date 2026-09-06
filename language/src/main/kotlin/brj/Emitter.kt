package brj

import brj.analyser.*
import brj.effects.collectEffectfulCallees
import brj.effects.inferEffects
import brj.nodes.BridjeRootNode
import brj.nodes.BridjeRootNodeGen
import brj.runtime.BridjeContext
import brj.runtime.BridjeFxMap
import brj.runtime.BridjeNull
import brj.runtime.HostClass
import com.oracle.truffle.api.bytecode.BytecodeLocal
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.strings.TruffleString

typealias Builder = BridjeRootNodeGen.Builder

/**
 * Where a value that outlives the expression producing it is read from, in the root currently
 * being emitted.
 *
 * The fx map and pre-applied effectful callees are held in locals within the function that
 * introduced them, and in the captures array of every function nested inside it.
 */
sealed interface ValueSource {
    fun emit(b: Builder)
}

data class LocalSource(val local: BytecodeLocal) : ValueSource {
    override fun emit(b: Builder) = b.emitLoadLocal(local)
}

data class CapturedSource(val index: Int) : ValueSource {
    override fun emit(b: Builder) = b.emitLoadCaptured(index)
}

/**
 * Emits one bytecode root — a top-level form, or a single function body.
 *
 * One instance corresponds to one root, holding that root's locals; nested functions get a
 * fresh instance sharing the same [Builder].
 *
 * @param source the root's source, or null where locations should not be recorded. Expressions
 *   introduced by macro expansion carry locations into a different source, and are skipped.
 */
class Emitter(
    private val lang: BridjeLanguage,
    private val ctx: BridjeContext,
    private val b: Builder,
    private val source: Source?,
    slotCount: Int,
) {
    private val locals: List<BytecodeLocal> = List(slotCount) { b.createLocal() }

    /** The flag local of the innermost enclosing `loop` (or function body), set by `recur`. */
    private var loopFlag: BytecodeLocal? = null

    private fun local(slot: Int) = locals[slot]

    private inline fun withLoc(loc: SourceSection?, body: () -> Unit) {
        val recordLoc = loc != null && source != null && loc.source == source
        if (recordLoc) b.beginSourceSection(loc!!.charIndex, loc.charLength)
        body()
        if (recordLoc) b.endSourceSection()
    }

    fun emitExpr(
        expr: ValueExpr,
        fxSource: ValueSource? = null,
        preApplied: Map<GlobalVar, ValueSource> = emptyMap(),
    ): Unit = withLoc(expr.loc) {
        when (expr) {
            is NilExpr -> b.emitLoadConstant(BridjeNull)
            is BoolExpr -> b.emitLoadConstant(expr.value)
            is IntExpr -> b.emitLoadConstant(expr.value)
            is DoubleExpr -> b.emitLoadConstant(expr.value)
            is BigIntExpr -> b.emitUnsupported("BigInt interop")
            is BigDecExpr -> b.emitUnsupported("BigDec interop")
            is StringExpr -> b.emitLoadConstant(TruffleString.fromConstant(expr.value, TruffleString.Encoding.UTF_8))

            is VectorExpr -> {
                b.beginMakeVector()
                expr.els.forEach { emitExpr(it, fxSource, preApplied) }
                b.endMakeVector()
            }

            is SetExpr -> TODO()

            is RecordExpr -> {
                b.beginMakeRecord(expr.fields.map { it.first }.toTypedArray())
                expr.fields.forEach { emitExpr(it.second, fxSource, preApplied) }
                b.endMakeRecord()
            }

            is LocalVarExpr -> b.emitLoadLocal(local(expr.localVar.slot))
            is CapturedVarExpr -> b.emitLoadCaptured(expr.captureIndex)
            is GlobalVarExpr -> b.emitLoadGlobalVar(expr.globalVar)
            is TruffleObjectExpr -> b.emitLoadConstant(expr.value)
            is HostStaticMethodExpr -> b.emitReadHostMember(expr.hostClass, expr.methodName)
            is HostConstructorExpr -> b.emitLoadConstant(HostClass(expr.hostClass))
            is QuoteExpr -> b.emitLoadConstant(expr.form)

            is LetExpr -> {
                b.beginBlock()
                b.beginStoreLocal(local(expr.localVar.slot))
                emitExpr(expr.bindingExpr, fxSource, preApplied)
                b.endStoreLocal()
                emitExpr(expr.bodyExpr, fxSource, preApplied)
                b.endBlock()
            }

            is FnExpr -> emitFn(expr, fxSource, preApplied)
            is CallExpr -> emitCall(expr, fxSource, preApplied)

            is DoExpr -> {
                b.beginBlock()
                expr.sideEffects.forEach { emitExpr(it, fxSource, preApplied) }
                emitExpr(expr.result, fxSource, preApplied)
                b.endBlock()
            }

            is IfExpr -> {
                b.beginConditional()
                b.beginAsBoolean()
                emitExpr(expr.predExpr, fxSource, preApplied)
                b.endAsBoolean()
                emitExpr(expr.thenExpr, fxSource, preApplied)
                emitExpr(expr.elseExpr, fxSource, preApplied)
                b.endConditional()
            }

            is CaseExpr -> emitCase(expr, fxSource, preApplied)
            is TryCatchExpr -> emitTryCatch(expr, fxSource, preApplied)

            is RecordSetExpr -> {
                b.beginSetRecordKey(expr.key)
                emitExpr(expr.recordExpr, fxSource, preApplied)
                emitExpr(expr.valueExpr, fxSource, preApplied)
                b.endSetRecordKey()
            }

            is RecordUpdateExpr -> {
                b.beginUpdateRecord(expr.fields.map { it.first }.toTypedArray())
                emitExpr(expr.recordExpr, fxSource, preApplied)
                expr.fields.forEach { emitExpr(it.second, fxSource, preApplied) }
                b.endUpdateRecord()
            }

            is EffectVarExpr ->
                if (fxSource != null) {
                    b.beginReadFxEntry(expr.effectVar)
                    fxSource.emit(b)
                    b.endReadFxEntry()
                } else {
                    b.emitLoadGlobalVar(expr.effectVar)
                }

            is WithFxExpr -> emitWithFx(expr, fxSource, preApplied)

            is LoopExpr -> {
                b.beginBlock()
                expr.bindings.forEach { (lv, init) ->
                    b.beginStoreLocal(local(lv.slot))
                    emitExpr(init, fxSource, preApplied)
                    b.endStoreLocal()
                }
                emitLoopBody(expr.bodyExpr, fxSource, preApplied)
                b.endBlock()
            }

            is RecurExpr -> emitRecur(expr, fxSource, preApplied)
            is LangExpr -> emitLang(expr)

            is ErrorValueExpr -> error("analyser error: ${expr.message}")
        }
    }

    private fun emitLang(expr: LangExpr) {
        val langSource = Source.newBuilder(expr.language, expr.code, "lang-${expr.language}").build()
        b.emitLoadConstant(ctx.truffleEnv.parsePublic(langSource).call())
    }

    private fun emitFxOr(fxSource: ValueSource?) {
        if (fxSource != null) fxSource.emit(b) else b.emitLoadConstant(BridjeFxMap.EMPTY)
    }

    private fun emitCall(expr: CallExpr, fxSource: ValueSource?, preApplied: Map<GlobalVar, ValueSource>) {
        val callee = (expr.fnExpr as? GlobalVarExpr)?.globalVar
        val preAppliedCallee = callee?.let { preApplied[it] }

        b.beginInvoke()

        when {
            preAppliedCallee != null -> preAppliedCallee.emit(b)

            // No pre-applied closure to hand, so apply the fx map here: callee(fx)(args).
            callee != null && callee.effects.isNotEmpty() -> {
                b.beginInvoke()
                emitExpr(expr.fnExpr, fxSource, preApplied)
                emitFxOr(fxSource)
                b.endInvoke()
            }

            else -> emitExpr(expr.fnExpr, fxSource, preApplied)
        }

        expr.argExprs.forEach { emitExpr(it, fxSource, preApplied) }
        b.endInvoke()
    }

    private fun emitWithFx(expr: WithFxExpr, fxSource: ValueSource?, preApplied: Map<GlobalVar, ValueSource>) {
        b.beginBlock()

        val fxLocal = b.createLocal()
        b.beginStoreLocal(fxLocal)
        b.beginBuildFxMap(expr.bindings.map { it.first }.toTypedArray())
        emitFxOr(fxSource)
        expr.bindings.forEach { emitExpr(it.second, fxSource, preApplied) }
        b.endBuildFxMap()
        b.endStoreLocal()

        val newPreApplied = preApplied + expr.bodyExpr.collectEffectfulCallees().toList().associateWith { callee ->
            val calleeLocal = b.createLocal()
            b.beginStoreLocal(calleeLocal)
            b.beginInvoke()
            b.emitLoadGlobalVar(callee)
            b.emitLoadLocal(fxLocal)
            b.endInvoke()
            b.endStoreLocal()
            LocalSource(calleeLocal)
        }

        emitExpr(expr.bodyExpr, LocalSource(fxLocal), newPreApplied)
        b.endBlock()
    }

    private fun emitCase(expr: CaseExpr, fxSource: ValueSource?, preApplied: Map<GlobalVar, ValueSource>) {
        b.beginBlock()

        val scrutinee = b.createLocal()
        val result = b.createLocal()

        b.beginStoreLocal(scrutinee)
        b.beginOrNil()
        emitExpr(expr.scrutinee, fxSource, preApplied)
        b.endOrNil()
        b.endStoreLocal()

        emitBranches(expr.branches, scrutinee, result, fxSource, preApplied) {
            b.beginStoreLocal(result)
            b.beginNoMatch()
            b.emitLoadLocal(scrutinee)
            b.endNoMatch()
            b.endStoreLocal()
        }

        b.emitLoadLocal(result)
        b.endBlock()
    }

    /**
     * Emits [branches] as a chain of `IfThenElse`, each storing its body's value into [result].
     *
     * [onNoMatch] closes the chain where no branch matched; a default branch ends it earlier,
     * making any branch after it unreachable.
     */
    private fun emitBranches(
        branches: List<CaseBranch>,
        scrutinee: BytecodeLocal,
        result: BytecodeLocal,
        fxSource: ValueSource?,
        preApplied: Map<GlobalVar, ValueSource>,
        onNoMatch: () -> Unit,
    ) {
        val branch = branches.firstOrNull() ?: return onNoMatch()

        fun emitBody(bindings: () -> Unit = {}) {
            b.beginBlock()
            bindings()
            b.beginStoreLocal(result)
            emitExpr(branch.bodyExpr, fxSource, preApplied)
            b.endStoreLocal()
            b.endBlock()
        }

        fun emitRest() = emitBranches(branches.drop(1), scrutinee, result, fxSource, preApplied, onNoMatch)

        when (val pattern = branch.pattern) {
            is DefaultPattern -> emitBody()

            is NilPattern -> {
                b.beginIfThenElse()
                b.beginIsNil()
                b.emitLoadLocal(scrutinee)
                b.endIsNil()
                emitBody()
                emitRest()
                b.endIfThenElse()
            }

            is CatchAllBindingPattern -> {
                b.beginIfThenElse()
                b.beginIsNotNil()
                b.emitLoadLocal(scrutinee)
                b.endIsNotNil()
                emitBody {
                    b.beginStoreLocal(local(pattern.binding.slot))
                    b.emitLoadLocal(scrutinee)
                    b.endStoreLocal()
                }
                emitRest()
                b.endIfThenElse()
            }

            is TagPattern -> {
                b.beginIfThenElse()
                b.beginMatchesTag(pattern.tagValue, pattern.bindings.size)
                b.emitLoadLocal(scrutinee)
                b.endMatchesTag()
                emitBody {
                    pattern.bindings.forEachIndexed { i, binding ->
                        b.beginStoreLocal(local(binding.slot))
                        b.beginTagField(i)
                        b.emitLoadLocal(scrutinee)
                        b.endTagField()
                        b.endStoreLocal()
                    }
                }
                emitRest()
                b.endIfThenElse()
            }
        }
    }

    private fun emitTryCatch(expr: TryCatchExpr, fxSource: ValueSource?, preApplied: Map<GlobalVar, ValueSource>) {
        b.beginBlock()

        val result = b.createLocal()
        val anomaly = b.createLocal()
        val discard = b.createLocal()

        fun emitTryCatchBody() {
            b.beginTryCatch()

            b.beginStoreLocal(result)
            emitExpr(expr.bodyExpr, fxSource, preApplied)
            b.endStoreLocal()

            b.beginBlock()
            b.beginStoreLocal(anomaly)
            b.beginToAnomaly()
            b.emitLoadException()
            b.endToAnomaly()
            b.endStoreLocal()
            emitBranches(expr.catchBranches, anomaly, result, fxSource, preApplied) {
                b.beginStoreLocal(result)
                b.beginRethrow()
                b.emitLoadLocal(anomaly)
                b.endRethrow()
                b.endStoreLocal()
            }
            b.endBlock()

            b.endTryCatch()
        }

        if (expr.finallyExpr != null) {
            b.beginTryFinally {
                b.beginStoreLocal(discard)
                emitExpr(expr.finallyExpr, fxSource, preApplied)
                b.endStoreLocal()
            }
            emitTryCatchBody()
            b.endTryFinally()
        } else {
            emitTryCatchBody()
        }

        b.emitLoadLocal(result)
        b.endBlock()
    }

    /**
     * Emits [body] as the body of a `recur` target, producing its value.
     *
     * `recur` writes the new binding values and raises the loop's flag, and the body then runs to
     * completion before the loop re-tests it. That is only equivalent to jumping to the loop head
     * because the analyser admits `recur` in tail position alone, where the body has nothing left
     * to evaluate.
     */
    private fun emitLoopBody(
        body: ValueExpr,
        fxSource: ValueSource?,
        preApplied: Map<GlobalVar, ValueSource>,
    ) {
        b.beginBlock()

        val flag = b.createLocal()
        val result = b.createLocal()

        b.beginStoreLocal(flag)
        b.emitLoadConstant(true)
        b.endStoreLocal()

        val enclosingFlag = loopFlag
        loopFlag = flag

        b.beginWhile()
        b.beginAsBoolean()
        b.emitLoadLocal(flag)
        b.endAsBoolean()

        b.beginBlock()
        b.beginStoreLocal(flag)
        b.emitLoadConstant(false)
        b.endStoreLocal()
        b.beginStoreLocal(result)
        emitExpr(body, fxSource, preApplied)
        b.endStoreLocal()
        b.endBlock()

        b.endWhile()

        loopFlag = enclosingFlag

        b.emitLoadLocal(result)
        b.endBlock()
    }

    private fun emitRecur(expr: RecurExpr, fxSource: ValueSource?, preApplied: Map<GlobalVar, ValueSource>) {
        val flag = loopFlag ?: error("recur outside a loop")

        b.beginBlock()

        // Every argument is evaluated before any binding is written, so a `recur` can permute
        // its own bindings.
        val temps = expr.argExprs.map { arg ->
            val temp = b.createLocal()
            b.beginStoreLocal(temp)
            emitExpr(arg, fxSource, preApplied)
            b.endStoreLocal()
            temp
        }

        expr.bindings.forEachIndexed { i, binding ->
            b.beginStoreLocal(local(binding.slot))
            b.emitLoadLocal(temps[i])
            b.endStoreLocal()
        }

        b.beginStoreLocal(flag)
        b.emitLoadConstant(true)
        b.endStoreLocal()

        b.emitLoadNull()
        b.endBlock()
    }

    private fun emitFn(expr: FnExpr, fxSource: ValueSource?, preApplied: Map<GlobalVar, ValueSource>) {
        val captures = expr.captures.map { captured ->
            when (val captureSource = captured.source) {
                is FrameSlotCapture -> LocalSource(local(captureSource.slot))
                is TransitiveCapture -> CapturedSource(captureSource.captureIndex)
            }
        }.toMutableList()

        // An inner fn reaches the fx map and any pre-applied callees through its own captures.
        val innerFxSource =
            if (fxSource != null && expr.bodyExpr.inferEffects().isNotEmpty()) {
                CapturedSource(captures.size).also { captures.add(fxSource) }
            } else null

        val innerPreApplied = expr.bodyExpr.collectEffectfulCallees().toList()
            .mapNotNull { callee -> preApplied[callee]?.let { callee to it } }
            .associate { (callee, source) ->
                callee to (CapturedSource(captures.size).also { captures.add(source) } as ValueSource)
            }

        val innerRoot = emitFnRoot(
            expr.params, expr.bodyExpr, expr.slotCount,
            hasCaptures = captures.isNotEmpty(),
            innerFxSource, innerPreApplied,
        )

        if (captures.isEmpty()) {
            b.emitLoadFunction(innerRoot)
        } else {
            b.beginMakeClosure(innerRoot)
            captures.forEach { it.emit(b) }
            b.endMakeClosure()
        }
    }

    /**
     * Emits a nested root for a function body, and returns it.
     *
     * A function with captures takes them as argument 0, so its parameters start at argument 1.
     */
    private fun emitFnRoot(
        params: List<LocalVar>,
        bodyExpr: ValueExpr,
        slotCount: Int,
        hasCaptures: Boolean,
        fxSource: ValueSource?,
        preApplied: Map<GlobalVar, ValueSource>,
    ): BridjeRootNode {
        b.beginRoot()
        val inner = Emitter(lang, ctx, b, source, slotCount)
        val argOffset = if (hasCaptures) 1 else 0

        b.beginBlock()
        params.forEachIndexed { i, param ->
            b.beginStoreLocal(inner.local(param.slot))
            b.emitLoadParam(argOffset + i)
            b.endStoreLocal()
        }

        b.beginReturn()
        inner.emitLoopBody(bodyExpr, fxSource, preApplied)
        b.endReturn()
        b.endBlock()

        return b.endRoot()
    }

    /**
     * Emits the two-stage root a `def` with effects compiles to: an outer function taking the fx
     * map, returning a closure over that map and over each effectful callee already applied to it.
     */
    fun emitEffectfulDefRoot(fnExpr: FnExpr): BridjeRootNode {
        b.beginRoot()
        b.beginBlock()

        val fxLocal = b.createLocal()
        b.beginStoreLocal(fxLocal)
        b.emitLoadParam(0)
        b.endStoreLocal()

        val callees = fnExpr.bodyExpr.collectEffectfulCallees().toList()

        val calleeLocals = callees.map { callee ->
            val calleeLocal = b.createLocal()
            b.beginStoreLocal(calleeLocal)
            b.beginInvoke()
            b.emitLoadGlobalVar(callee)
            b.emitLoadLocal(fxLocal)
            b.endInvoke()
            b.endStoreLocal()
            calleeLocal
        }

        val preApplied = callees
            .mapIndexed { i, callee -> callee to (CapturedSource(1 + i) as ValueSource) }
            .toMap()

        val innerRoot = emitFnRoot(
            fnExpr.params, fnExpr.bodyExpr, fnExpr.slotCount,
            hasCaptures = true,
            CapturedSource(0), preApplied,
        )

        b.beginReturn()
        b.beginMakeClosure(innerRoot)
        b.emitLoadLocal(fxLocal)
        calleeLocals.forEach { b.emitLoadLocal(it) }
        b.endMakeClosure()
        b.endReturn()

        b.endBlock()
        return b.endRoot()
    }
}
