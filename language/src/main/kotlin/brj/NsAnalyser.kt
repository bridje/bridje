package brj

import brj.runtime.BridjeContext
import brj.runtime.NsContext
import brj.runtime.Symbol.Companion.sym

private val NS = "ns".sym

internal class NsAnalyser(private val env: BridjeContext) {

    @Suppress("NAME_SHADOWING")
    internal fun analyseNs(z: Zip<Form>): NsContext {
        if (z.znode !is ListForm) TODO("expected list")
        var z = z.zdown ?: TODO("expected `ns` symbol")
        z.znode.run { if(this !is SymbolForm || sym != NS) TODO("expected `ns` symbol") }

        z = z.zright ?: TODO("expected ns symbol")
        val ns = ((z.znode as? SymbolForm) ?: TODO("expected symbol")).sym

        return NsContext(env, ns)
    }
}

internal fun Zip<Form>.analyseNs(ctx: BridjeContext) = NsAnalyser(ctx).analyseNs(this)

internal fun Zip<Form>.isNsForm() =
    znode is ListForm && (zdown?.znode as? SymbolForm)?.sym == NS
