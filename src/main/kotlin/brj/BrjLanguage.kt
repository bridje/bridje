package brj

import brj.BrjLanguage.BridjeContext
import brj.Symbol.Companion.mkSym
import brj.ValueExprEmitter.Companion.emitValueExpr
import brj.analyser.ValueExpr
import brj.analyser.analyseValueExpr
import brj.types.valueExprType
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
@Suppress("unused")
class BrjLanguage : TruffleLanguage<BridjeContext>() {

    class BridjeContext internal constructor(val truffleEnv: TruffleLanguage.Env, var env: brj.Env)

    override fun createContext(truffleEnv: TruffleLanguage.Env) = BridjeContext(truffleEnv, Env())

    override fun isObjectOfLanguage(obj: Any): Boolean =
        obj is RecordObject || obj is VariantObject || obj is BridjeFunction

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val source = request.source

        val env = getCtx().env
        val forms = readForms(source.reader)

        val expr = analyseValueExpr(env, NSEnv(USER), forms)

        val valueExprType = valueExprType(expr, null)

        val noDefaultImpls = valueExprType.effects.filterNot { (env.nses.getValue(it.ns).vars.getValue(it.base) as EffectVar).hasDefault }
        if (noDefaultImpls.isNotEmpty()) throw IllegalArgumentException("not all effects have implementations: $noDefaultImpls")

        println("type: $valueExprType")

        return emitValueExpr(expr)
    }

    internal class BrjNSLoader(private val sources: Map<Symbol, Source> = emptyMap()) : NSFormLoader {
        private fun nsSource(ns: Symbol): Source? =
            this::class.java.getResource("/${ns.baseStr.replace('.', '/')}.brj")
                ?.let { url -> Source.newBuilder("brj", url).build() }

        override fun loadNSForms(ns: Symbol): List<Form> =
            readForms((sources[ns] ?: nsSource(ns) ?: TODO("ns not found")).reader)
    }

    internal object BrjEmitter : Emitter {
        override fun evalValueExpr(expr: ValueExpr) = ValueExprEmitter.evalValueExpr(expr)
        override fun emitJavaImport(javaImport: JavaImport) = JavaImportEmitter.emitJavaImport(javaImport)
        override fun emitRecordKey(recordKey: RecordKey) = RecordEmitter.emitRecordKey(recordKey)
        override fun emitVariantKey(variantKey: VariantKey) = VariantEmitter.emitVariantKey(variantKey)
        override fun evalEffectExpr(sym: QSymbol, defaultImpl: BridjeFunction?) = EffectEmitter.emitEffectExpr(sym, defaultImpl)
    }

    companion object {
        private val USER = mkSym("user")

        internal fun getLang() = getCurrentLanguage(BrjLanguage::class.java)
        internal fun getCtx() = getCurrentContext(BrjLanguage::class.java)

        fun require(env: brj.Env, rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()): brj.Env {
            val evaluator = Evaluator(env, BrjNSLoader(sources), BrjEmitter)

            evaluator.requireNSes(rootNses)

            return evaluator.env
        }

        fun require(rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()) {
            Context.getCurrent().initialize("brj")

            val ctx = getCtx()

            synchronized(ctx) {
                if (ctx.env.nses.isEmpty()) {
                    ctx.env = require(ctx.env, setOf(mkSym("brj.forms")))
                }

                ctx.env = require(ctx.env, rootNses, sources)
            }
        }
    }
}
