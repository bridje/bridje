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
import java.math.BigDecimal
import java.math.BigInteger

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
@Suppress("unused")
class BrjLanguage : TruffleLanguage<BridjeContext>() {

    class BridjeContext internal constructor(val truffleEnv: Env, var env: brj.Env)

    override fun createContext(truffleEnv: Env) = BridjeContext(truffleEnv, Env())

    override fun isObjectOfLanguage(obj: Any): Boolean =
        obj is RecordObject || obj is VariantObject || obj is BridjeFunction

    override fun parse(request: ParsingRequest): CallTarget {
        val source = request.source

        val env = getCtx().env
        val form = readForms(source.reader).first()

        val expr = analyseValueExpr(env, NSEnv(USER), BrjEmitter, form)

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

        private fun toVariant(form: Form): Any {
            val arg = when (form.arg) {
                is List<*> -> form.arg.map { toVariant(it as Form) }
                is Form -> toVariant(form)
                else -> form.arg
            }

            return (getCtx().env.nses.getValue(form.qsym.ns).vars[form.qsym.base]?.value as BridjeFunction).callTarget.call(arg)
        }

        private fun fromVariant(obj: VariantObject): Form {
            val arg = obj.dynamicObject[0]

            fun fromVariantList(arg: Any): List<Form> {
                return (arg as List<*>).map { fromVariant(it as VariantObject) }
            }

            return when (obj.variantKey.sym.base.baseStr) {
                "BooleanForm" -> BooleanForm(arg as Boolean)
                "StringForm" -> StringForm(arg as String)
                "IntForm" -> IntForm(arg as Long)
                "FloatForm" -> FloatForm(arg as Double)
                "BigIntForm" -> BigIntForm(arg as BigInteger)
                "BigFloatForm" -> BigFloatForm(arg as BigDecimal)
                "ListForm" -> ListForm(fromVariantList(arg))
                "VectorForm" -> VectorForm(fromVariantList(arg))
                "SetForm" -> SetForm(fromVariantList(arg))
                "RecordForm" -> RecordForm(fromVariantList(arg))
                "SymbolForm" -> SymbolForm(arg as Symbol)
                "QSymbolForm" -> QSymbolForm(arg as QSymbol)
                "QuotedSymbolForm" -> QuotedSymbolForm(arg as Symbol)
                "QuotedQSymbolForm" -> QuotedQSymbolForm(arg as QSymbol)
                else -> TODO()
            }
        }

        override fun evalMacro(macroVar: DefMacroVar, args: List<Form>): Form {
            val macroFn = macroVar.value as BridjeFunction

            return fromVariant(macroFn.callTarget.call(*(args.map(::toVariant).toTypedArray())) as VariantObject)
        }

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

        fun require(rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()): brj.Env {
            Context.getCurrent().initialize("brj")

            val ctx = getCtx()

            synchronized(ctx) {
                if (ctx.env.nses.isEmpty()) {
                    ctx.env = require(ctx.env, setOf(mkSym("brj.forms")))
                }

                ctx.env = require(ctx.env, rootNses, sources)
            }

            return ctx.env
        }
    }
}
