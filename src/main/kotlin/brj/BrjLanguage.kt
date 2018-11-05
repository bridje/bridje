package brj

import brj.ActionExprAnalyser.DefDataExpr.DefDataConstructor
import brj.Analyser.AnalyserState
import brj.BrjEnv.NSEnv
import brj.BrjEnv.NSEnv.Companion.nsAnalyser
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

        val env = ctx.env
        val forms = readForms(source.reader)

        val expr = ValueExpr.ValueExprAnalyser(env, env.nses[USER] ?: NSEnv(USER)).analyseValueExpr(forms)

        println("type: ${Types.TypeChecker(env).valueExprTyping(expr)}")

        val emitter = ValueNode.ValueNodeEmitter(this, FrameDescriptor())
        return Truffle.getRuntime().createCallTarget(emitter.RootValueNode(emitter.emitValueExpr(expr)))
    }

    internal inner class Require(var env: BrjEnv) {
        fun evalDataDefs(nsFile: NSFile) {
            fun dataDefEvaluator(it: AnalyserState) {
                val analyser = ActionExprAnalyser(env, nsFile.nsEnv)

                it.nested(ListForm::forms) {
                    when (it.expectForm<Form.SymbolForm>().sym) {
                        DO -> it.varargs(::dataDefEvaluator)

                        DEF_DATA -> {
                            val (sym, typeVars) = analyser.defDataSigAnalyser(it)

                            nsFile.nsEnv += NSEnv.DataType(sym, typeVars, emptyList())
                            env += nsFile.nsEnv
                        }

                        TYPE_DEF, DEF -> Unit

                        else -> TODO()
                    }
                }
            }

            AnalyserState(nsFile.forms).varargs(::dataDefEvaluator)
        }

        fun evalTypeDefs(nsFile: NSFile) {
            fun typeDefEvaluator(it: AnalyserState) {
                it.nested(ListForm::forms) {
                    val analyser = ActionExprAnalyser(env, nsFile.nsEnv)

                    when (it.expectForm<Form.SymbolForm>().sym) {
                        DO -> it.varargs(::typeDefEvaluator)

                        TYPE_DEF -> {
                            val typeDef = analyser.typeDefAnalyser(it)

                            if (nsFile.nsEnv.vars[typeDef.sym] != null) {
                                TODO("sym already exists in NS")
                            }

                            nsFile.nsEnv += NSEnv.GlobalVar(typeDef.sym, typeDef.typing, null)
                            env += nsFile.nsEnv
                        }

                        DEF_DATA -> {
                            val defDataExpr = analyser.defDataAnalyser(it)

                            nsFile.nsEnv += NSEnv.DataType(defDataExpr.sym, defDataExpr.typeParams, defDataExpr.constructors.map(DefDataConstructor::sym))

                            val dataType = Types.MonoType.DataType(NamespacedSymbol.create(nsFile.ns, defDataExpr.sym))

                            defDataExpr.constructors.forEach { constructor ->
                                val type = constructor.params?.let { params -> Types.MonoType.FnType(params, dataType) }
                                    ?: dataType
                                nsFile.nsEnv += NSEnv.GlobalVar(constructor.sym, Types.Typing(type), null)
                            }

                            env += nsFile.nsEnv
                        }

                        DEF -> Unit

                        else -> TODO()
                    }

                    return@nested
                }
            }

            AnalyserState(nsFile.forms).varargs(::typeDefEvaluator)
        }

        fun evalVars(nsFile: NSFile) {
            fun evalDefExpr(expr: ActionExprAnalyser.DefExpr) {
                val expectedTyping = nsFile.nsEnv.vars[expr.sym]?.typing

                if (expectedTyping != null && !(expr.typing.matches(expectedTyping))) {
                    TODO()
                }

                val frameDescriptor = FrameDescriptor()

                val node = ValueNode.ValueNodeEmitter(this@BrjLanguage, frameDescriptor).emitValueExpr(expr.expr)

                nsFile.nsEnv += NSEnv.GlobalVar(expr.sym, expectedTyping
                    ?: expr.typing, node.execute(Truffle.getRuntime().createVirtualFrame(emptyArray(), frameDescriptor)))

                env += nsFile.nsEnv
            }

            fun varEvaluator(it: AnalyserState) {
                it.nested(ListForm::forms) {
                    val analyser = ActionExprAnalyser(env, nsFile.nsEnv)

                    when (it.expectForm<Form.SymbolForm>().sym) {
                        DO -> it.varargs(::varEvaluator)
                        DEF -> evalDefExpr(analyser.defAnalyser(it))

                        DEF_DATA -> {
                            val nsEnv = nsFile.nsEnv
                            val sym = analyser.defDataSigAnalyser(it).first
                            val dataType = nsEnv.dataTypes[sym]!!

                            dataType.constructors.forEach { constructorSym ->
                                nsFile.nsEnv += nsEnv.vars[constructorSym]!!.copy(value = null)

                                env += nsFile.nsEnv
                                TODO()
                            }
                        }

                        TYPE_DEF -> Unit

                        else -> TODO()
                    }

                    return@nested
                }
            }

            AnalyserState(nsFile.forms).varargs(::varEvaluator)
        }
    }

    private fun require(rootNses: Set<Symbol>, sources: Map<Symbol, Source>) {
        synchronized(this) {
            val ctx = ctx

            val req = Require(ctx.env)

            requireOrder(readNsFiles(rootNses, sources)).forEach { nses ->
                nses.forEach(req::evalDataDefs)
                nses.forEach(req::evalTypeDefs)
                nses.forEach(req::evalVars)
            }

            ctx.env = req.env
        }
    }

    companion object {
        private val DO = Symbol.create("do")

        private val DEF = Symbol.create("def")
        private val TYPE_DEF = Symbol.create("::")
        private val DEFX = Symbol.create("defx")
        private val DEF_DATA = Symbol.create("defdata")

        private val langSource = Source.newBuilder("brj", "lang", null).internal(true).buildLiteral()

        private fun getLang() = Context.getCurrent().eval(langSource).asHostObject<BrjLanguage>()

        private fun nsSource(ns: Symbol): Source? =
            this::class.java.getResource("${ns.name.replace('.', '/')}.brj")
                ?.let { url -> Source.newBuilder("brj", url).build() }

        internal data class NSFile(val ns: Symbol, var nsEnv: BrjEnv.NSEnv, val forms: List<Form>)

        private fun readNsFiles(rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()): Map<Symbol, NSFile> {
            val nses: MutableList<Symbol> = rootNses.toMutableList()
            val nsFiles: MutableMap<Symbol, NSFile> = mutableMapOf()

            while (nses.isNotEmpty()) {
                val ns = nses.removeAt(0)
                if (nsFiles.containsKey(ns)) continue

                val source = sources[ns] ?: nsSource(ns) ?: TODO("ns not found")

                val state = AnalyserState(readForms(source.reader))
                val nsEnv = nsAnalyser(state)
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

        fun require(rootNses: Set<Symbol>, sources: Map<Symbol, Source> = emptyMap()) {
            getLang().require(rootNses, sources)
        }
    }

}
