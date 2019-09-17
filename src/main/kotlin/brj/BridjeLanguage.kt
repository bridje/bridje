package brj

import brj.analyser.FormsParser
import brj.analyser.NSHeader
import brj.analyser.NSHeader.Companion.nsHeaderParser
import brj.analyser.ParserState
import brj.emitter.BridjeObject
import brj.emitter.Symbol
import brj.emitter.Symbol.Companion.mkSym
import brj.emitter.SymbolKind.VAR_SYM
import brj.emitter.TruffleEmitter
import brj.emitter.ValueNode
import brj.reader.Form
import brj.reader.FormReader.Companion.readSourceForms
import brj.reader.ListForm
import brj.reader.NSForms
import brj.reader.NSForms.Loader.Companion.ClasspathLoader
import brj.reader.RecordForm
import brj.runtime.RuntimeEnv
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
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
        internal fun require(ctx: BridjeContext, rootNses: Set<Symbol>, nsFormLoader: NSForms.Loader? = null): RuntimeEnv {
            val evaluator = Evaluator(ctx.env, TruffleEmitter(ctx))

            NSForms.loadNSes(rootNses, nsFormLoader ?: ClasspathLoader()).forEach(evaluator::evalNS)

            return evaluator.env
        }
    }
}

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
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
        override fun execute(frame: VirtualFrame) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val reqs = formsParser(ParserState(readSourceForms(request.source)))

        return Truffle.getRuntime().createCallTarget(EvalRootNode(this, reqs))

//        return if (ns != null) {
//            Truffle.getRuntime().createCallTarget(object : RootNode(this) {
//                override fun execute(frame: VirtualFrame) = require(ctx, setOf(ns), BridjeNSLoader(forms = mapOf(ns to forms)))
//            })
//        } else {
//            val expr = ValueExprAnalyser(env, NSEnv(USER)).analyseValueExpr(forms.first())
//
//            val valueExprType = valueExprType(expr, null)
//
//            val noDefaultImpls = valueExprType.effects.filterNot { (env.nses[it.ns]!!.vars.getValue(it.base) as EffectVar).hasDefault }
//            if (noDefaultImpls.isNotEmpty()) throw IllegalArgumentException("not all effects have implementations: $noDefaultImpls")
//
//            println("type: $valueExprType")
//
//            ValueExprEmitter(ctx).emitValueExpr(expr)
//        }
    }

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
