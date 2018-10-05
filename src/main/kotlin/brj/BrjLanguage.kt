package brj

import brj.ActionExpr.ActionExprAnalyser
import brj.ActionExpr.ActionExprAnalyser.Companion.nsAnalyser
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

    private fun brjEnv() = getCurrentContext(this.javaClass).env

    override fun parse(request: TruffleLanguage.ParsingRequest): CallTarget {
        val source = request.source

        if (source.isInternal) {
            when (source.characters) {
                "env" -> return Truffle.getRuntime().createCallTarget(object : RootNode(this) {
                    override fun execute(frame: VirtualFrame?): Any {
                        CompilerDirectives.transferToInterpreter()
                        val ctx = contextReference.get()
                        return ctx.truffleEnv.asGuestValue(ctx.env)
                    }
                })
            }
        }

        val forms = readForms(source.reader)

        val expr = ValueExpr.ValueExprAnalyser(brjEnv(), Symbol("user")).analyseValueExpr(forms)

        val emitterCtx = ValueNode.ValueNodeEmitter(this, FrameDescriptor())
        return Truffle.getRuntime().createCallTarget(emitterCtx.RootValueNode(emitterCtx.emitValueExpr(expr)))
    }

    companion object {
        private val envSource = Source.newBuilder("brj", "env", null).internal(true).buildLiteral()

        private fun getEnv(ctx: Context): BrjEnv = ctx.eval(envSource).asHostObject<BrjEnv>()

        fun require(ctx: Context, source: Source) {
            val state = Analyser.AnalyserState(readForms(source.reader))
            val ns = nsAnalyser(state)

            val ana = ActionExprAnalyser(getEnv(ctx), ns)

            while (state.forms.isNotEmpty()) {
                println(state.nested(ListForm::forms, ana.actionExprAnalyser))
            }

            TODO()
        }
    }
}
