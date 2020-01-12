package brj

import brj.analyser.*
import brj.analyser.NSHeader.Companion.nsHeaderParser
import brj.emitter.*
import brj.reader.*
import brj.reader.FormReader.Companion.readSourceForms
import brj.runtime.BridjeFunction
import brj.runtime.QSymbol
import brj.runtime.RuntimeEnv
import brj.runtime.SymKind.ID
import brj.runtime.Symbol
import brj.types.valueExprType
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Scope
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import java.math.BigDecimal
import java.math.BigInteger

typealias Loc = SourceSection

@ExportLibrary(InteropLibrary::class)
private class GlobalScope(val ctx: BridjeContext) : BridjeObject {
    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) = ctx.truffleEnv.asGuestValue(ctx.env.nses.values.flatMap { it.vars.values }.associateBy { it.sym.toString() })

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(k: String): Boolean {
        val qsym = QSymbol(k)
        return ctx.env.nses[qsym.ns]?.vars?.containsKey(qsym.local) ?: false
    }

    @ExportMessage
    @TruffleBoundary
    fun readMember(k: String): Any? {
        val qsym = QSymbol(k)
        return ctx.env.nses[qsym.ns]?.vars?.get(qsym.local)?.value
    }
}

class BridjeContext internal constructor(internal val language: BridjeLanguage,
                                         internal val truffleEnv: TruffleLanguage.Env,
                                         internal var env: RuntimeEnv = RuntimeEnv()) {

    private val formLoader = ClasspathLoader(truffleEnv)

    internal fun makeRootNode(node: ValueNode, frameDescriptor: FrameDescriptor = FrameDescriptor()) =
        object : RootNode(language, frameDescriptor) {
            @Child
            var valueNode = node

            override fun execute(frame: VirtualFrame) = valueNode.execute(frame)
        }

    @TruffleBoundary
    internal fun require(ns: Symbol): RuntimeEnv {
        val evaluator = Evaluator(TruffleEmitter(this))

        synchronized(this) {
            env = nsForms(ns, formLoader)
                .fold(env) { env, forms ->
                    evaluator.evalNS(env, forms)
                }
        }

        return env
    }
}

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.0",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
@Suppress("unused")
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    override fun createContext(truffleEnv: Env) =
        BridjeContext(this, truffleEnv)

    override fun initializeContext(ctx: BridjeContext) {
        ctx.env += formsNSEnv(ctx)
        ctx.require(Symbol(ID, "brj.core"))
    }

    override fun isObjectOfLanguage(obj: Any) = obj is BridjeObject

    internal sealed class ParseRequest {
        data class RequireRequest(val ns: Symbol) : ParseRequest()
        data class AliasRequest(val aliases: Map<Symbol, Symbol>) : ParseRequest()
        data class ValueRequest(val form: Form) : ParseRequest()
        data class NSRequest(val nsHeader: NSHeader, val forms: List<Form>) : ParseRequest()
    }

    private val requireParser: FormsParser<Symbol?> = {
        it.maybe {
            it.nested(ListForm::forms) { it.expectSym(Symbol(ID, "require!")); it }
        }?.expectSym(ID)
    }

    private val aliasParser: FormsParser<Map<Symbol, Symbol>?> = {
        it.maybe {
            it.nested(ListForm::forms) { it.expectSym(Symbol(ID, "alias!")); it }
        }?.let {
            it.nested(RecordForm::forms) {
                it.varargs { Pair(it.expectSym(ID), it.expectSym(ID)) }
            }.toMap()
        }
    }

    private val formsParser: FormsParser<List<ParseRequest>> = {
        it.varargs {
            it.or(
                { it.maybe(::nsHeaderParser)?.let { header -> ParseRequest.NSRequest(header, it.consume()) } },
                { it.maybe(requireParser)?.let { nses -> ParseRequest.RequireRequest(nses) } },
                { it.maybe(aliasParser)?.let { aliases -> ParseRequest.AliasRequest(aliases) } }
                ) ?: ParseRequest.ValueRequest(it.expectForm())
        }
    }

    internal class EvalRootNode(val lang: BridjeLanguage, val reqs: List<ParseRequest>) : RootNode(lang) {
        private val ctxRef = lookupContextReference(BridjeLanguage::class.java)

        @TruffleBoundary
        private fun evalValueRequest(req: ParseRequest.ValueRequest): Any? {
            val ctx = ctxRef.get()

            val resolver = Resolver.NSResolver(ctx.env)
            val expr = ValueExprAnalyser(resolver).analyseValueExpr(req.form)

            @Suppress("UNUSED_VARIABLE") // for now
            val valueExprType = valueExprType(expr, null)

            val fnExpr = FnExpr(params = emptyList(), expr = expr, closedOverLocals = setOf(DEFAULT_EFFECT_LOCAL))
            val fn = (ValueExprEmitter(ctx).evalValueExpr(fnExpr)) as BridjeFunction

            return fn.execute(emptyArray<Any>())
        }

        @TruffleBoundary
        private fun evalRequireRequest(req: ParseRequest.RequireRequest): Any {
            ctxRef.get().require(req.ns)
            return lang.findTopScopes(ctxRef.get())
        }

        override fun execute(frame: VirtualFrame) =
            reqs.fold(null as Any?) { _, req ->
                when (req) {
                    is ParseRequest.ValueRequest -> evalValueRequest(req)
                    is ParseRequest.RequireRequest -> evalRequireRequest(req)
                    is ParseRequest.AliasRequest -> TODO()
                    is ParseRequest.NSRequest -> TODO()
                }
            }
    }

    private val readFormsNode = object : RootNode(null) {
        @TruffleBoundary
        override fun execute(frame: VirtualFrame) =
            TruffleLanguage.getCurrentContext(BridjeLanguage::class.java).truffleEnv.asGuestValue(
                readSourceForms(Source.newBuilder("brj", frame.arguments[1] as String, "<read-forms>").build()))
    }

    override fun parse(request: ParsingRequest): RootCallTarget =
        Truffle.getRuntime().createCallTarget(
            if (request.source.isInternal) {
                when(request.source.characters) {
                    "read-forms" -> RootNode.createConstantNode(BridjeFunction(readFormsNode))
                    else -> TODO()
                }
            } else {
                EvalRootNode(this, formsParser(ParserState(readSourceForms(request.source))))
            })

    override fun findTopScopes(ctx: BridjeContext): Iterable<Scope> {
        return listOf(Scope.newBuilder("global", GlobalScope(ctx)).build())
    }

    companion object {
        internal fun currentBridjeContext() = getCurrentContext(BridjeLanguage::class.java)
    }
}
