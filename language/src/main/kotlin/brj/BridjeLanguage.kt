package brj

import brj.Reader.Companion.readForms
import brj.analyser.*
import brj.runtime.*
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.*
import com.oracle.truffle.api.dsl.Bind
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source

@Registration(
    id = "bridje",
    name = "bridje",
    defaultMimeType = "text/brj",
    characterMimeTypes = ["text/brj"],
    contextPolicy = ContextPolicy.EXCLUSIVE
)
@Bind.DefaultExpression("get(\$node)")
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    companion object {
        private val LANGUAGE_REF: LanguageReference<BridjeLanguage> = LanguageReference.create(BridjeLanguage::class.java)

        @JvmStatic
        fun get(node: Node): BridjeLanguage = LANGUAGE_REF.get(node)

        private fun nsNameToResourcePath(nsName: String): String =
            nsName.replace(':', '/') + ".brj"
    }

    override fun createContext(env: Env) = BridjeContext(env, this)

    override fun getScope(context: BridjeContext): Any = BridjeScope(context)

    class EvalNode(
        lang: BridjeLanguage,
        frameDescriptor: FrameDescriptor,
        @field:Child private var node: BridjeNode
    ) : RootNode(lang, frameDescriptor) {
        override fun execute(frame: VirtualFrame) = node.execute(frame)
    }

    private fun buildFrameDescriptor(slotCount: Int): FrameDescriptor {
        val builder = FrameDescriptor.newBuilder()
        repeat(slotCount) {
            builder.addSlot(FrameSlotKind.Illegal, null, null)
        }
        return builder.build()
    }


    override fun parse(request: ParsingRequest): CallTarget {
        val (nsDecl, forms) = request.source.readForms().toList().analyseNs()

        return object : RootNode(this) {
            @TruffleBoundary
            private fun loadNamespaceFromClasspath(ctx: BridjeContext, fqNs: String): NsEnv {
                if (fqNs in ctx.loadingInProgress) {
                    error("Circular dependency detected: $fqNs")
                }

                ctx.loadingInProgress.add(fqNs)

                try {
                    val resourcePath = nsNameToResourcePath(fqNs)
                    val resourceUrl = BridjeLanguage::class.java.classLoader.getResource(resourcePath)
                        ?: error("Namespace not found on classpath: $fqNs (looked for $resourcePath)")

                    val source = Source.newBuilder("bridje", resourceUrl).build()

                    val callTarget = ctx.truffleEnv.parsePublic(source)
                    callTarget.call()

                    return ctx.namespaces[fqNs]
                        ?: error("Namespace $fqNs not registered after loading from $resourcePath")
                } finally {
                    ctx.loadingInProgress.remove(fqNs)
                }
            }

            private fun resolveRequires(ctx: BridjeContext): Requires =
                nsDecl
                    ?.requires.orEmpty()
                    .mapValues { (_, fqNs) ->
                        ctx.namespaces[fqNs]
                            ?: loadNamespaceFromClasspath(ctx, fqNs)
                    }

            private fun NsDecl.resolve(ctx: BridjeContext): NsEnv =
                NsEnv(
                    requires = resolveRequires(ctx),
                    imports = this.imports,
                    nsDecl = this,
                    forms = forms
                )

            @TruffleBoundary
            private fun evalExpr(expr: ValueExpr, slotCount: Int): Any? {
                val frameDescriptor = buildFrameDescriptor(slotCount)
                val emitter = Emitter(this@BridjeLanguage)
                val node = emitter.emitExpr(expr)
                return EvalNode(this@BridjeLanguage, frameDescriptor, node).callTarget.call()
            }

            @TruffleBoundary
            private fun List<Form>.evalForms(ctx: BridjeContext, nsEnv: NsEnv): Pair<NsEnv, Any?> {
                var nsEnv = nsEnv
                val errors = mutableListOf<Analyser.Error>()

                val res = fold(null as Any?) { _, form ->
                    val analyser = Analyser(ctx, nsEnv)

                    when (val expr = analyser.analyse(form)) {
                        is TopLevelDo -> {
                            val (newNsEnv, res) = expr.forms.evalForms(ctx, nsEnv)
                            nsEnv = newNsEnv
                            res
                        }

                        is DefExpr -> {
                            val value = evalExpr(expr.valueExpr, analyser.slotCount)
                            nsEnv = nsEnv.def(expr.name, value)
                            value
                        }

                        is DefTagExpr -> {
                            val value: Any =
                            if (expr.fieldNames.isEmpty()) {
                                BridjeTaggedSingleton(expr.name)
                            } else {
                                BridjeTagConstructor(expr.name, expr.fieldNames.size, expr.fieldNames)
                            }

                            nsEnv = nsEnv.def(expr.name, value)

                            value
                        }

                        is DefMacroExpr -> {
                            val fn = evalExpr(expr.fn, analyser.slotCount)
                            val macro = BridjeMacro(fn!!)
                            nsEnv = nsEnv.def(expr.name, macro)
                            macro
                        }

                        is DefKeyExpr -> {
                            val key = BridjeKey(expr.name)
                            nsEnv = nsEnv.defKey(expr.name, key)
                            key
                        }

                        is ValueExpr -> evalExpr(expr, analyser.slotCount)

                        is AnalyserErrors -> {
                            errors.addAll(expr.errors)
                            null
                        }
                    }
                }

                return when {
                    errors.isEmpty() -> Pair(nsEnv, res)
                    else -> throw Analyser.Errors(errors)
                }
            }

            @TruffleBoundary
            override fun execute(frame: VirtualFrame): Any? {
                val ctx = BridjeContext.get(this)

                // Before evaluating this namespace, re-evaluate any quarantined dependencies
                if (nsDecl != null) {
                    val quarantinedDeps = ctx.globalEnv.getQuarantinedDependencies(nsDecl)
                    
                    for ((depName, quarantinedNs) in quarantinedDeps) {
                        try {
                            // Re-evaluate the quarantined dependency
                            val depNsDecl = quarantinedNs.nsDecl
                            val depForms = quarantinedNs.forms
                            
                            // Resolve dependencies for this quarantined namespace
                            fun resolveQuarantinedRequires(): Requires =
                                depNsDecl.requires.mapValues { (_, fqNs) ->
                                    ctx.namespaces[fqNs]
                                        ?: loadNamespaceFromClasspath(ctx, fqNs)
                                }
                            
                            val depInitialEnv = NsEnv(
                                requires = resolveQuarantinedRequires(),
                                imports = depNsDecl.imports,
                                nsDecl = depNsDecl,
                                forms = depForms
                            )
                            
                            val (depNsEnv, _) = depForms.evalForms(ctx, depInitialEnv)
                            
                            // Register the re-evaluated namespace
                            ctx.updateGlobalEnv { globalEnv ->
                                globalEnv.withNamespace(depName, depNsEnv.copy(nsDecl = depNsDecl, forms = depForms))
                            }
                        } catch (e: Exception) {
                            // Halt at first failure
                            throw RuntimeException("Failed to re-evaluate quarantined dependency $depName: ${e.message}", e)
                        }
                    }
                }

                val (nsEnv, result) = forms.evalForms(ctx, nsDecl?.resolve(ctx) ?: NsEnv())

                if (nsDecl != null) {
                    ctx.updateGlobalEnv { globalEnv ->
                        // Invalidate this namespace and all its dependents before re-registering
                        val invalidated = globalEnv.invalidateNamespace(nsDecl.name)
                        // Register the new version
                        invalidated.withNamespace(nsDecl.name, nsEnv.copy(nsDecl = nsDecl, forms = forms))
                    }
                }

                return result
            }
        }.callTarget
    }
}
