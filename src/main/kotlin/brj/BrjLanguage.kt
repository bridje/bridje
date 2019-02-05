package brj

import brj.BrjLanguage.BridjeContext
import brj.ValueExprEmitter.Companion.emitValueExpr
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

    data class BridjeContext(val truffleEnv: TruffleLanguage.Env, var env: brj.Env)

    override fun createContext(env: TruffleLanguage.Env) = BridjeContext(env, brj.Env())

    override fun isObjectOfLanguage(obj: Any): Boolean =
        obj is VariantObject || obj is BridjeFunction

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val source = request.source

        val env = getCtx().env
        val forms = readForms(source.reader)

        val expr = analyseValueExpr(env, NSEnv(USER), forms)

        println("type: ${valueExprType(expr)}")

        return emitValueExpr(expr)
    }

    companion object {
        private val USER = Symbol.mkSym("user")

        internal fun getLang() = getCurrentLanguage(BrjLanguage::class.java)
        internal fun getCtx() = getCurrentContext(BrjLanguage::class.java)

        fun require(rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()) {
            Context.getCurrent().initialize("brj")

            val ctx = getCtx()

            synchronized(ctx) {
                val evaluator = Evaluator(ctx.env, object : Emitter {
                    override fun evalValueExpr(expr: ValueExpr) = ValueExprEmitter.evalValueExpr(expr)
                    override fun emitJavaImport(javaImport: JavaImport) = JavaImportEmitter.emitJavaImport(javaImport)
                    override fun emitVariant(variantKey: VariantKey) = VariantEmitter.emitVariant(variantKey)
                })

                loadNSes(rootNses) { ns ->
                    fun nsSource(ns: Symbol): Source? =
                        this::class.java.getResource("${ns.baseStr.replace('.', '/')}.brj")
                            ?.let { url -> Source.newBuilder("brj", url).build() }

                    readForms((sources[ns] ?: nsSource(ns) ?: TODO("ns not found")).reader)
                }.forEach(evaluator::evalNS)

                ctx.env = evaluator.env
            }
        }
    }
}
