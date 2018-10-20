package brj

import brj.ActionExpr.ActionExprAnalyser
import brj.ActionExpr.ActionExprAnalyser.Companion.nsAnalyser
import brj.ActionExpr.DefExpr
import brj.BrjEnv.NSEnv
import brj.BrjEnv.NSEnv.GlobalVar
import brj.BrjLanguage.BridjeContext
import brj.Form.Companion.readForms
import brj.Form.ListForm
import brj.ValueNode.FnNode.BridjeFunction
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.pcollections.HashTreePSet
import org.pcollections.TreePVector
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

    data class BridjeContext(val truffleEnv: TruffleLanguage.Env, var env: BrjEnv)

    override fun createContext(env: TruffleLanguage.Env) = BridjeContext(env, brj.BrjEnv())

    override fun isObjectOfLanguage(obj: Any): Boolean = false

    @TypeSystem(
        Boolean::class, String::class,
        Long::class, Float::class, BigInteger::class, BigDecimal::class,
        BridjeFunction::class,
        TreePVector::class, HashTreePSet::class)
    abstract class BridjeTypes

    private val ctx get() = getCurrentContext(this.javaClass)

    private val USER = Symbol.create("user")

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val source = request.source

        if (source.isInternal) {
            when (source.characters) {
                "lang" ->
                    return Truffle.getRuntime().createCallTarget(object : RootNode(this) {
                        override fun execute(frame: VirtualFrame): Any {
                            CompilerDirectives.transferToInterpreter()
                            return contextReference.get().truffleEnv.asGuestValue(this@BrjLanguage)
                        }
                    })
            }
        }

        val forms = readForms(source.reader)

        val expr = ValueExpr.ValueExprAnalyser(ctx.env, USER).analyseValueExpr(forms)

        println("type: ${Types.TypeChecker(ctx.env).valueExprTyping(expr)}")

        val emitter = ValueNode.ValueNodeEmitter(this, FrameDescriptor())
        return Truffle.getRuntime().createCallTarget(emitter.RootValueNode(emitter.emitValueExpr(expr)))
    }

    companion object {

        private val langSource = Source.newBuilder("brj", "lang", null).internal(true).buildLiteral()

        private fun getLang(ctx: Context): BrjLanguage = ctx.eval(langSource).asHostObject<BrjLanguage>()

        private fun nsSource(ns: Symbol): Source? =
            this::class.java.getResource("${ns.name.replace('.', '/')}.brj")
                ?.let { url -> Source.newBuilder("brj", url).build() }

        fun require(graalCtx: Context, ns: Symbol, sources: Map<Symbol, Source> = emptyMap()) {
            val lang = getLang(graalCtx)
            val ctx = lang.ctx

            val source = sources[ns] ?: nsSource(ns) ?: TODO("ns not found")

            val state = Analyser.AnalyserState(readForms(source.reader))
            val analysedNs = nsAnalyser(state)

            val ana = ActionExprAnalyser(ctx.env, ns)

            var nsEnv = NSEnv()

            while (state.forms.isNotEmpty()) {
                val expr = state.nested(ListForm::forms, ana.actionExprAnalyser)

                val frameDescriptor = FrameDescriptor()
                val typeChecker = Types.TypeChecker(ctx.env + (ns to nsEnv))
                val emitter = ValueNode.ValueNodeEmitter(lang, frameDescriptor)

                when (expr) {
                    is DefExpr -> {
                        val valueExpr =
                            if (expr.params == null) expr.expr
                            else ValueExpr.FnExpr(params = expr.params, expr = expr.expr)

                        val typing = typeChecker.valueExprTyping(valueExpr)

                        val node = emitter.emitValueExpr(valueExpr)

                        nsEnv += expr.sym to GlobalVar(node.execute(Truffle.getRuntime().createVirtualFrame(emptyArray(), frameDescriptor)), typing)
                    }
                }
            }

            ctx.env += ns to nsEnv
        }
    }
}
