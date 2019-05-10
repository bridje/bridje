package brj

import brj.Symbol.Companion.mkSym
import brj.analyser.ValueExprAnalyser
import brj.analyser.parseNSSym
import brj.types.valueExprType
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
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import java.math.BigDecimal
import java.math.BigInteger

@TypeSystem(
    Boolean::class, String::class,
    Long::class, Double::class,
    BigInteger::class, BigDecimal::class,
    BridjeFunction::class, RecordObject::class, VariantObject::class)
internal abstract class BridjeTypes

@TypeSystemReference(BridjeTypes::class)
@NodeInfo(language = "bridje")
internal abstract class ValueNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any
}

internal class BridjeNSLoader(private val sources: Map<Symbol, Source> = emptyMap(),
                              private val forms: Map<Symbol, List<Form>> = emptyMap()) : NSFormLoader {
    private fun nsSource(ns: Symbol): Source? =
        this::class.java.getResource("/${ns.baseStr.replace('.', '/')}.brj")
            ?.let { url -> Source.newBuilder("bridje", url).build() }

    override fun loadNSForms(ns: Symbol): List<Form> =
        forms[ns] ?: readForms((sources[ns] ?: nsSource(ns) ?: TODO("ns not found")).reader)
}

class BridjeContext internal constructor(internal var env: RuntimeEnv,
                                         internal val language: BridjeLanguage,
                                         internal val truffleEnv: TruffleLanguage.Env) {

    internal fun makeRootNode(node: ValueNode, frameDescriptor: FrameDescriptor = FrameDescriptor()) =
        object : RootNode(language, frameDescriptor) {
            override fun execute(frame: VirtualFrame) = node.execute(frame)
        }
}

private val USER = mkSym("user")

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

        ctx.env = require(ctx, setOf(mkSym("brj.forms"), mkSym("brj.core")))

        return ctx
    }

    override fun isObjectOfLanguage(obj: Any): Boolean =
        obj is RecordObject || obj is VariantObject || obj is BridjeFunction

    override fun parse(request: ParsingRequest): CallTarget {
        val source = request.source

        val ctx = contextReference.get()

        val env = ctx.env
        val forms = readForms(source.reader)

        val ns = parseNSSym(forms)

        return if (ns != null) {
            Truffle.getRuntime().createCallTarget(object : RootNode(this) {
                override fun execute(frame: VirtualFrame) = require(ctx, setOf(ns), BridjeNSLoader(forms = mapOf(ns to forms)))
            })
        } else {
            val expr = ValueExprAnalyser(env, NSEnv(USER), TruffleMacroEvaluator(ctx)).analyseValueExpr(forms.first())

            val valueExprType = valueExprType(expr, null)

            val noDefaultImpls = valueExprType.effects.filterNot { (env.nses[it.ns]!!.vars.getValue(it.base) as EffectVar).hasDefault }
            if (noDefaultImpls.isNotEmpty()) throw IllegalArgumentException("not all effects have implementations: $noDefaultImpls")

            println("type: $valueExprType")

            ValueExprEmitter(ctx).emitValueExpr(expr)
        }
    }

    companion object {
        internal fun currentBridjeContext() = getCurrentContext(BridjeLanguage::class.java)!!

        @TruffleBoundary
        internal fun require(ctx: BridjeContext, rootNses: Set<Symbol>, nsFormLoader: NSFormLoader = BridjeNSLoader()): RuntimeEnv {
            val evaluator = Evaluator(ctx.env, nsFormLoader, TruffleEmitter(ctx), TruffleMacroEvaluator(ctx))

            evaluator.requireNSes(rootNses)

            return evaluator.env
        }

        internal fun require(rootNses: Set<Symbol>, nsFormLoader: NSFormLoader): RuntimeEnv {
            Context.getCurrent().initialize("bridje")

            val ctx = currentBridjeContext()

            synchronized(ctx) {
                ctx.env = require(ctx, rootNses, nsFormLoader)
            }

            return ctx.env
        }

        @JvmStatic
        fun require(rootNses: Set<Symbol>) = require(rootNses, BridjeNSLoader())
    }
}
