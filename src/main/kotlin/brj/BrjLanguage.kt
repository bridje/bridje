package brj

import brj.BrjLanguage.BridjeContext
import brj.Reader.readForms
import brj.ValueNode.FnNode.BridjeFunction
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.frame.FrameDescriptor
import org.pcollections.HashTreePSet
import org.pcollections.TreePVector
import java.math.BigDecimal
import java.math.BigInteger

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE)
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
        val forms = readForms(request.source)

        val expr = ValueExpr.AnalyserCtx(brjEnv(), Symbol("user")).analyseValueExpr(forms)

        val emitterCtx = ValueNode.EmitterCtx(this, FrameDescriptor())
        return Truffle.getRuntime().createCallTarget(emitterCtx.RootValueNode(emitterCtx.emitValueExpr(expr)))
    }
}
