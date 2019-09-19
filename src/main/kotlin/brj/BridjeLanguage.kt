package brj

import brj.analyser.*
import brj.analyser.NSHeader.Companion.nsHeaderParser
import brj.emitter.BridjeObject
import brj.emitter.TruffleEmitter
import brj.emitter.ValueExprEmitter
import brj.emitter.ValueNode
import brj.reader.Form
import brj.reader.FormReader.Companion.readSourceForms
import brj.reader.ListForm
import brj.reader.NSForms
import brj.reader.NSForms.Loader.Companion.ClasspathLoader
import brj.reader.RecordForm
import brj.runtime.EffectVar
import brj.runtime.RuntimeEnv
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.mkSym
import brj.runtime.SymbolKind.VAR_SYM
import brj.types.valueExprType
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection
import org.graalvm.polyglot.Context
import java.math.BigDecimal
import java.math.BigInteger

typealias Loc = SourceSection

class BridjeContext internal constructor(internal val language: BridjeLanguage,
                                         internal val truffleEnv: TruffleLanguage.Env,
                                         internal var env: RuntimeEnv = RuntimeEnv()) {

    internal fun makeRootNode(node: ValueNode, frameDescriptor: FrameDescriptor = FrameDescriptor()) =
        object : RootNode(language, frameDescriptor) {
            override fun execute(frame: VirtualFrame) = node.execute(frame)
        }

    internal fun require(rootNses: Set<Symbol>, nsFormLoader: NSForms.Loader? = null): RuntimeEnv {
        Context.getCurrent().initialize("brj")

        synchronized(this) {
            env = require(this, rootNses, nsFormLoader)
        }

        return env
    }

    companion object {
        @TruffleBoundary
        internal fun require(ctx: BridjeContext, rootNses: Set<Symbol>, nsFormLoader: NSForms.Loader? = null): RuntimeEnv =
            NSForms.loadNSes(rootNses, nsFormLoader ?: ClasspathLoader())
                .fold(ctx.env) { env, forms ->
                    Evaluator(TruffleEmitter(ctx, Resolver.NSResolver(env))).evalNS(env, forms)
                }
    }
}

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.1",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE)
@Suppress("unused")
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    override fun createContext(truffleEnv: Env) =
        BridjeContext(this, truffleEnv)

    override fun initializeContext(ctx: BridjeContext) {
        ctx.require(setOf(mkSym("brj.forms"), mkSym("brj.core")))
    }

    override fun isObjectOfLanguage(obj: Any) = obj is BridjeObject

    internal sealed class ParseRequest {
        data class RequireRequest(val nses: Set<Symbol>) : ParseRequest()
        data class AliasRequest(val aliases: Map<Symbol, Symbol>) : ParseRequest()
        data class ValueRequest(val form: Form) : ParseRequest()
        data class NSRequest(val nsHeader: NSHeader, val forms: List<Form>) : ParseRequest()
    }

    private val requireParser: FormsParser<Set<Symbol>?> = {
        it.maybe {
            it.nested(ListForm::forms) { it.expectSym(mkSym("require!")); it }
        }?.let { it.varargs { it.expectSym(VAR_SYM) }.toSet() }
    }

    private val aliasParser: FormsParser<Map<Symbol, Symbol>?> = {
        it.maybe {
            it.nested(ListForm::forms) { it.expectSym(mkSym("alias!")); it }
        }?.let {
            it.nested(RecordForm::forms) {
                it.varargs { Pair(it.expectSym(VAR_SYM), it.expectSym(VAR_SYM)) }
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

    internal class EvalRootNode(lang: BridjeLanguage, val reqs: List<ParseRequest>) : RootNode(lang) {
        private val ctxRef = lang.contextReference

        @TruffleBoundary
        private fun evalValueRequest(req: ParseRequest.ValueRequest): Any? {
            val ctx = ctxRef.get()
            val resolver = Resolver.NSResolver(ctx.env)
            val expr = ValueExprAnalyser(resolver).analyseValueExpr(req.form)

            val valueExprType = valueExprType(expr, null)

            val noDefaultImpls = valueExprType.effects.filterNot { (resolver.resolveVar(it) as EffectVar).hasDefault }
            require(noDefaultImpls.isEmpty()) { "not all effects have implementations: $noDefaultImpls" }

            return ValueExprEmitter(ctx).evalValueExpr(expr)
        }

        override fun execute(frame: VirtualFrame): Any? =
            reqs.fold(null as Any?) { _, req ->
                when (req) {
                    is ParseRequest.ValueRequest -> evalValueRequest(req)
                    is ParseRequest.RequireRequest -> TODO()
                    is ParseRequest.AliasRequest -> TODO()
                    is ParseRequest.NSRequest -> TODO()
                }
            }
    }

    override fun parse(request: ParsingRequest): RootCallTarget =
        Truffle.getRuntime().createCallTarget(
            EvalRootNode(
                this,
                formsParser(ParserState(readSourceForms(request.source)))))

    override fun toString(context: BridjeContext, value: Any): String {
        return toString(value)
    }

    companion object {
        internal fun currentBridjeContext() = getCurrentContext(BridjeLanguage::class.java)

        internal fun require(rootNses: Set<Symbol>, nsFormLoader: NSForms.Loader? = null) {
            Context.getCurrent().initialize("brj")
            currentBridjeContext().require(rootNses, nsFormLoader)
        }

        internal fun toString(value: Any) = when (value) {
            is BigInteger -> "${value}N"
            is BigDecimal -> "${value}M"
            is List<*> -> "[${value.joinToString(" ")}]"
            else -> value.toString()
        }
    }
}
