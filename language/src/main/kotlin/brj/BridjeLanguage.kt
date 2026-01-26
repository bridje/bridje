package brj

import brj.Reader.Companion.readForms
import brj.analyser.analyseNs
import brj.nodes.ParseRootNode
import brj.nodes.RequireNsNode
import brj.runtime.BridjeContext
import brj.runtime.BridjeScope
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.*
import com.oracle.truffle.api.dsl.Bind
import com.oracle.truffle.api.nodes.Node
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
    }

    override fun createContext(env: Env) = BridjeContext(env, this)

    override fun initializeContext(context: BridjeContext) {
        val coreUrl = BridjeLanguage::class.java.classLoader.getResource("brj/core.brj")
            ?: error("brj/core.brj not found on classpath")
        val source = Source.newBuilder("bridje", coreUrl).build()
        context.truffleEnv.parsePublic(source).call()
    }

    override fun getScope(context: BridjeContext): Any = BridjeScope(context)

    override fun parse(request: ParsingRequest): CallTarget {
        val source = request.source
        val (nsDecl, forms) = source.readForms().toList().analyseNs()

        val requireNodes = nsDecl?.requires.orEmpty().map { (alias, fqNs) ->
            RequireNsNode(alias, fqNs)
        }.toTypedArray()

        return ParseRootNode(this, nsDecl, forms, source, requireNodes).callTarget
    }
}
