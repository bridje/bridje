package brj

import brj.Symbol.Companion.mkSym
import brj.SymbolKind.VAR_SYM
import brj.analyser.FormsParser
import brj.analyser.NSHeader
import brj.analyser.NSHeader.Companion.nsHeaderParser
import brj.analyser.ParserState
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import org.graalvm.polyglot.Context
import java.math.BigDecimal
import java.math.BigInteger

typealias Loc = SourceSection

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Double::class,
    BigInteger::class, BigDecimal::class,
    BridjeFunction::class, RecordObject::class, VariantObject::class)
internal abstract class BridjeTypes

@TypeSystemReference(BridjeTypes::class)
@NodeInfo(language = "bridje")
internal abstract class ValueNode(val loc: Loc?) : Node() {
    abstract fun execute(frame: VirtualFrame): Any

    override fun getSourceSection() = loc
}

internal fun readSourceForms(source: Source) =
    FormReader(source).readForms(source.reader)

internal class BridjeNSLoader(private val sources: Map<Symbol, Source> = emptyMap(),
                              private val forms: Map<Symbol, List<Form>> = emptyMap()) : NSForms.Loader {
    private fun nsSource(ns: Symbol): Source? =
        this::class.java.getResource("/${ns.baseStr.replace('.', '/')}.brj")
            ?.let { url -> Source.newBuilder("bridje", url).build() }

    override fun loadForms(ns: Symbol): List<Form> =
        forms[ns] ?: readSourceForms(sources[ns] ?: nsSource(ns) ?: TODO("ns not found"))
}

class BridjeContext internal constructor(internal var env: RuntimeEnv,
                                         internal val language: BridjeLanguage,
                                         internal val truffleEnv: TruffleLanguage.Env) {

    internal fun makeRootNode(node: ValueNode, frameDescriptor: FrameDescriptor = FrameDescriptor()) =
        object : RootNode(language, frameDescriptor) {
            override fun execute(frame: VirtualFrame) = node.execute(frame)
        }
}

@TruffleLanguage.Registration(
    id = "bridje",
    name = "Bridje",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
@Suppress("unused")
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    override fun createContext(truffleEnv: Env): BridjeContext {
        val ctx = BridjeContext(RuntimeEnv(), this, truffleEnv)

        // TODO re-require core
        // ctx.env = require(ctx, setOf(mkSym("brj.forms"), mkSym("brj.core")))

        return ctx
    }

    override fun isObjectOfLanguage(obj: Any): Boolean =
        obj is RecordObject || obj is VariantObject || obj is BridjeFunction

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
        it.maybe { it.nested(ListForm::forms) { it.expectSym(mkSym("alias!")); it } }?.let {
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

    override fun parse(request: ParsingRequest): CallTarget {
        val source = request.source

        val ctx = contextReference.get()

        val env = ctx.env

        val reqs = formsParser(ParserState(readSourceForms(source))).toString()

        return Truffle.getRuntime().createCallTarget(object : RootNode(this) {
            override fun execute(frame: VirtualFrame?): Any = reqs
        })

//        val ns = parseNSSym(forms)
//
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

    companion object {
        internal fun currentBridjeContext() = getCurrentContext(BridjeLanguage::class.java)!!

        @TruffleBoundary
        internal fun require(ctx: BridjeContext, rootNses: Set<Symbol>, nsFormLoader: NSForms.Loader = BridjeNSLoader()): RuntimeEnv {
            val evaluator = Evaluator(ctx.env, TruffleEmitter(ctx))

            NSForms.loadNSes(rootNses, nsFormLoader).forEach(evaluator::evalNS)

            return evaluator.env
        }

        internal fun require(rootNses: Set<Symbol>, nsFormLoader: NSForms.Loader): RuntimeEnv {
            Context.getCurrent().initialize("bridje")

            val ctx = currentBridjeContext()

            synchronized(ctx) {
                ctx.env = require(ctx, rootNses, nsFormLoader)
            }

            return ctx.env
        }

        @JvmStatic
        internal fun require(rootNses: Set<Symbol>) = require(rootNses, BridjeNSLoader())
    }
}
