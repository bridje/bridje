package brj

import brj.runtime.BridjeContext
import brj.runtime.NsContext
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.interop.TruffleObject

private val NS = "ns".sym
private val IMPORTS = "imports".sym
private val REFERS = "refers".sym
private val ALIASES = "aliases".sym

internal class NsAnalyser(private val env: BridjeContext) {

    private fun Zip<Form>.analyseImports(): Map<Symbol, TruffleObject> {
        val imports = mutableMapOf<Symbol, TruffleObject>()
        val z = zright ?: TODO("expected form")
        if (z.znode !is RecordForm) TODO("expected record")

        var importZ = z.zdown

        while (importZ != null) {
            val packageSym = (importZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")
            importZ = importZ.zright ?: TODO("missing form")
            if (importZ.znode !is SetForm) TODO("expected set")

            importZ.zdown.zrights.map {
                val sym = (it.znode as? SymbolForm)?.sym ?: TODO("expected sym")
                val import = "$packageSym.$sym"
                env.truffleEnv.lookupHostSymbol(import) ?: TODO("can't find import: $import")
            }

            importZ = importZ.zright
        }
        return imports
    }

    @Suppress("NAME_SHADOWING")
    internal fun analyseNs(z: Zip<Form>): NsContext {
        if (z.znode !is ListForm) TODO("expected list")
        var z = z.zdown ?: TODO("expected `ns` symbol")
        z.znode.run { if(this !is SymbolForm || sym != NS) TODO("expected `ns` symbol") }

        z = z.zright ?: TODO("expected ns symbol")
        val ns = ((z.znode as? SymbolForm) ?: TODO("expected symbol")).sym

        var imports: Map<Symbol, TruffleObject>? = null

        z.zright?.let {
            if (it.znode !is RecordForm) TODO("expected record")
            var optsZ = it.zdown
            while (optsZ != null) {
                val kw = (optsZ.znode as? KeywordForm)?.sym

                optsZ = optsZ.zright ?: TODO("missing form")
                when (kw) {
                    IMPORTS -> imports = optsZ.analyseImports()
                    ALIASES -> TODO()
                    REFERS -> TODO()

                    null -> TODO("expected keyword")
                    else -> TODO("unexpected keyword ${kw}")
                }

                optsZ = optsZ.zright
            }
        }

        return NsContext(env, ns, 
            imports = imports ?: emptyMap())
    }
}

internal fun Zip<Form>.analyseNs(ctx: BridjeContext) = NsAnalyser(ctx).analyseNs(this)

internal fun Zip<Form>.isNsForm() =
    znode is ListForm && (zdown?.znode as? SymbolForm)?.sym == NS
