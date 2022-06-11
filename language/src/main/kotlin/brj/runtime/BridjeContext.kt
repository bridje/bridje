package brj.runtime

import brj.*
import brj.nodes.*
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleContext
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.Source

internal fun <R> TruffleContext.inContext(f: TruffleContext.() -> R): R {
    val prev = enter(null)
    return try {
        f()
    } finally {
        leave(null, prev)
    }
}

@ExportLibrary(InteropLibrary::class)
class BridjeContext(internal val lang: BridjeLanguage, internal val truffleEnv: TruffleLanguage.Env) : TruffleObject {

    internal val globalVars = mutableMapOf<Symbol, GlobalVar>()
    internal val imports = mutableMapOf<Symbol, TruffleObject>()

    internal val interop = InteropLibrary.getUncached()

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) =
        BridjeVector(globalVars.keys.map { it.local }.toList().toTypedArray())

    @ExportMessage
    fun isMemberReadable(key: String) = globalVars.containsKey(key.sym)

    @ExportMessage
    fun readMember(key: String) = globalVars[key.sym]!!.bridjeVar.value

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") allowSideEffects: Boolean) = "BridjeEnv"

    @TruffleBoundary
    fun def(sym: Symbol, typing: Typing, value: Any) {
        CompilerAsserts.neverPartOfCompilation()
        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) {
//                if (typing.res != globalVar.typing.res) TODO()
                globalVar.also {
                    when (it) {
                        is DefVar -> it.bridjeVar.set(value)
                        is DefxVar -> it.defaultImpl.set(value)
                    }
                }
            } else DefVar(sym, typing, BridjeVar(value))
        }
    }

    @TruffleBoundary
    fun defx(sym: Symbol, typing: Typing) {
        CompilerAsserts.neverPartOfCompilation()

        val defaultImplVar = BridjeVar(null)

        val value = BridjeFunction(
            DefxRootNodeGen.DefxValueRootNodeGen.create(lang, FrameDescriptor(), sym, defaultImplVar).callTarget
        )

        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) TODO("global var already exists in `defx`, '$sym'")
            else DefxVar(sym, typing, BridjeVar(value), defaultImplVar)
        }
    }

    @TruffleBoundary
    fun importClass(className: Symbol) {
        val clazz = truffleEnv.lookupHostSymbol(className.local) as TruffleObject
        val simpleClassName = interop.asString(interop.getMetaSimpleName(clazz)).sym
        imports[simpleClassName] = clazz
    }

    @TruffleBoundary
    fun poly(lang: String, code: String): Any =
        truffleEnv.parsePublic(Source.newBuilder(lang, code, "<brj-inline>").build()).call()

    @TruffleBoundary
    fun evalForm(form: Form): Any? {
        val rootNode = when (val doOrExpr = Analyser(this).analyseExpr(form)) {
            is TopLevelDo -> EvalRootNodeGen.create(lang, doOrExpr.forms)
            is TopLevelExpr -> when (val expr = doOrExpr.expr) {
                is ValueExpr -> {
                    // TODO
                    // typeLogger.info("type: ${valueExprTyping(expr)}")

                    val frameDescriptor = FrameDescriptor()
                    ValueExprRootNodeGen.create(
                        lang, frameDescriptor,
                        WriteLocalNodeGen.create(
                            lang, ReadArgNode(lang, 0),
                            frameDescriptor.findOrAddFrameSlot(DEFAULT_FX_LOCAL)
                        ),
                        ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr)
                    )
                }

                is DefExpr -> {
                    // TODO
                    // val valueExprTyping = valueExprTyping(expr.expr)
                    // typeLogger.info("type: $valueExprTyping")

                    val frameDescriptor = FrameDescriptor()
                    DefRootNodeGen.create(
                        lang, frameDescriptor,
                        expr.sym, Typing(TypeVar()), /*valueExprTyping*/ expr.loc,
                        ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr.expr),
                    )
                }

                is DefxExpr -> DefxRootNodeGen.create(lang, expr.sym, expr.typing, expr.loc)

                is ImportExpr -> ImportRootNodeGen.create(lang, expr.loc, expr.syms.toTypedArray())
            }
        }

        val callTarget = rootNode.callTarget
        return truffleEnv.context.inContext { callTarget.call(FxMap(FxMap.DEFAULT_SHAPE)) }
    }
}