package brj

import brj.ActionExpr.ActionExprAnalyser
import brj.ActionExpr.DefExpr
import brj.BrjEnv.NSEnv.Companion.nsAnalyser
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
import java.util.*
import kotlin.math.min

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

        private val lang get() = Context.getCurrent().eval(langSource).asHostObject<BrjLanguage>()

        private fun nsSource(ns: Symbol): Source? =
            this::class.java.getResource("${ns.name.replace('.', '/')}.brj")
                ?.let { url -> Source.newBuilder("brj", url).build() }

        private data class NSFile(val ns: Symbol, val nsEnv: BrjEnv.NSEnv, val forms: List<Form>)

        private fun readNsFiles(rootNs: Symbol, sources: Map<Symbol, Source> = emptyMap()): Map<Symbol, NSFile> {
            val nses: MutableList<Symbol> = mutableListOf(rootNs)
            val nsFiles: MutableMap<Symbol, NSFile> = mutableMapOf()

            while (nses.isNotEmpty()) {
                val ns = nses.removeAt(0)
                if (nsFiles.containsKey(ns)) continue

                val source = sources[ns] ?: nsSource(ns) ?: TODO("ns not found")

                val state = Analyser.AnalyserState(readForms(source.reader))
                var nsEnv = nsAnalyser(state)
                nses += (nsEnv.deps - nsFiles.keys)
                nsFiles[ns] = NSFile(ns, nsEnv, state.forms)
            }

            return nsFiles
        }

        private fun requireOrder(nsFiles: Map<Symbol, NSFile>): List<Set<NSFile>> {
            // https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm

            data class TarjanNS(val ns: Symbol, val nsFile: NSFile, var index: Int? = null, var lowlink: Int? = null, var isOnStack: Boolean = false)

            val nses: Map<Symbol, TarjanNS> = nsFiles.mapValues { TarjanNS(ns = it.key, nsFile = it.value) }
            var index = 0
            val res: MutableList<Set<NSFile>> = mutableListOf()
            val stack: MutableList<TarjanNS> = LinkedList()

            fun strongConnect(ns: TarjanNS) {
                ns.index = index
                ns.lowlink = index
                ns.isOnStack = true
                stack.add(0, ns)
                index++

                ns.nsFile.nsEnv.deps.map { nses[it]!! }.forEach { dep ->
                    if (dep.index == null) {
                        strongConnect(dep)
                        ns.lowlink = min(ns.lowlink!!, dep.lowlink!!)
                    } else if (dep.isOnStack) {
                        ns.lowlink = min(ns.lowlink!!, dep.index!!)
                    }
                }

                if (ns.lowlink!! == ns.index!!) {
                    val nsSet = mutableSetOf<NSFile>()
                    while (true) {
                        val dep = stack.removeAt(0)
                        dep.isOnStack = false
                        nsSet += dep.nsFile
                        if (dep == ns) break
                    }
                    res += nsSet
                }
            }

            nses.values.forEach { tarjanNS ->
                if (tarjanNS.index == null) {
                    strongConnect(tarjanNS)
                }
            }

            return res
        }

        fun require(rootNs: Symbol, sources: Map<Symbol, Source> = emptyMap()) {
            val ctx = lang.ctx

            var env = ctx.env

            requireOrder(readNsFiles(rootNs, sources)).forEach { nses ->
                nses.forEach { nsFile ->
                    val ns = nsFile.ns
                    var nsEnv = nsFile.nsEnv

                    env += ns to nsEnv
                    val state = Analyser.AnalyserState(nsFile.forms)

                    while (state.forms.isNotEmpty()) {
                        val expr = state.nested(ListForm::forms, ActionExprAnalyser(env, ns).actionExprAnalyser)

                        val frameDescriptor = FrameDescriptor()
                        val typeChecker = Types.TypeChecker(env)
                        val emitter = ValueNode.ValueNodeEmitter(lang, frameDescriptor)

                        when (expr) {
                            is DefExpr -> {
                                val valueExpr =
                                    if (expr.params == null) expr.expr
                                    else ValueExpr.FnExpr(params = expr.params, expr = expr.expr)

                                val typing = typeChecker.valueExprTyping(valueExpr)

                                val node = emitter.emitValueExpr(valueExpr)

                                nsEnv += expr.sym to GlobalVar(node.execute(Truffle.getRuntime().createVirtualFrame(emptyArray(), frameDescriptor)), typing)
                                env += ns to nsEnv
                            }
                        }
                    }
                }
            }

            ctx.env = env
        }
    }
}
