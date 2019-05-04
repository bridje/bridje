package brj

import brj.Symbol.Companion.mkSym
import brj.analyser.ValueExprAnalyser
import brj.types.valueExprType
import com.oracle.truffle.api.CallTarget
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

class BridjeContext internal constructor(internal val language: BridjeLanguage, internal val truffleEnv: TruffleLanguage.Env, internal var env: RuntimeEnv) {
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
        val ctx = BridjeContext(this, truffleEnv, RuntimeEnv())

        ctx.env = require(ctx, setOf(mkSym("brj.forms"), mkSym("brj.core")))

        return ctx
    }

    override fun isObjectOfLanguage(obj: Any): Boolean =
        obj is RecordObject || obj is VariantObject || obj is BridjeFunction

    override fun parse(request: ParsingRequest): CallTarget {
        val source = request.source

        val ctx = contextReference.get()

        val env = ctx.env
        val form = readForms(source.reader).first()

        val expr = ValueExprAnalyser(env, NSEnv(USER), TruffleMacroEvaluator(ctx)).analyseValueExpr(form)

        val valueExprType = valueExprType(expr, null)

        val noDefaultImpls = valueExprType.effects.filterNot { (env.nses.getValue(it.ns).vars.getValue(it.base) as EffectVar).hasDefault }
        if (noDefaultImpls.isNotEmpty()) throw IllegalArgumentException("not all effects have implementations: $noDefaultImpls")

        println("type: $valueExprType")

        return ValueExprEmitter(ctx).emitValueExpr(expr)
    }

    internal class BridjeNSLoader(private val sources: Map<Symbol, Source> = emptyMap()) : NSFormLoader {
        private fun nsSource(ns: Symbol): Source? =
            this::class.java.getResource("/${ns.baseStr.replace('.', '/')}.brj")
                ?.let { url -> Source.newBuilder("bridje", url).build() }

        override fun loadNSForms(ns: Symbol): List<Form> =
            readForms((sources[ns] ?: nsSource(ns) ?: TODO("ns not found")).reader)
    }

    internal fun require(ctx: BridjeContext, rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()): RuntimeEnv {
        val evaluator = Evaluator(ctx.env, BridjeNSLoader(sources), TruffleEmitter(ctx), TruffleMacroEvaluator(ctx))

        evaluator.requireNSes(rootNses)

        return evaluator.env
    }

    companion object {
        internal fun currentBridjeContext() = getCurrentContext(BridjeLanguage::class.java)!!

        fun require(rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()): RuntimeEnv {
            Context.getCurrent().initialize("bridje")

            val lang = getCurrentLanguage(BridjeLanguage::class.java)
            val ctx = getCurrentContext(BridjeLanguage::class.java)

            lang.require(ctx, rootNses, sources)

            return ctx.env
        }
    }
}
