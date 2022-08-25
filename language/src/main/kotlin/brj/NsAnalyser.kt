package brj

import brj.runtime.BridjeContext
import brj.runtime.GlobalVar
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
                val obj = (env.truffleEnv.lookupHostSymbol(import) as? TruffleObject) ?: TODO("can't find import: $import")

                imports[sym] = obj
            }

            importZ = importZ.zright
        }
        return imports
    }

    private fun Zip<Form>.analyseAliases(): Map<Symbol, Symbol> {
        val aliases = mutableMapOf<Symbol, Symbol>()
        val z = zright ?: TODO("expected form")
        if (z.znode !is RecordForm) TODO("expected record")

        var aliasZ = z.zdown

        while (aliasZ != null) {
            val aliasSym = (aliasZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")

            aliasZ = aliasZ.zright ?: TODO("missing form")
            val nsSym = ((aliasZ.znode as? SymbolForm) ?: TODO("expected symbol")).sym

            if (env[nsSym] == null) TODO("unknown ns")
            aliases[aliasSym] = nsSym

            aliasZ = aliasZ.zright
        }
        return aliases
    }

    private fun Zip<Form>.analyseRefers(): Map<Symbol, GlobalVar> {
        val refers = mutableMapOf<Symbol, GlobalVar>()
        val z = zright ?: TODO("expected form")
        if (z.znode !is RecordForm) TODO("expected record")

        var referZ = z.zdown

        while (referZ != null) {
            val nsSym = (referZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")
            val nsVars = (env[nsSym] ?: TODO("unknown ns")).globalVars

            referZ = referZ.zright ?: TODO("missing form")
            if (referZ.znode !is SetForm) TODO("expected set")

            referZ.zdown.zrights.map {
                val sym = (it.znode as? SymbolForm)?.sym ?: TODO("expected sym")
                val globalVar = nsVars[sym] ?: TODO("can't find refer: $nsSym/$sym")

                refers[sym] = globalVar
            }

            referZ = referZ.zright
        }
        return refers
    }

    internal fun Zip<Form>.analyseNs(): NsContext {
        if (znode !is ListForm) TODO("expected list")
        var z = zdown ?: TODO("expected `ns` symbol")
        z.znode.run { if (this !is SymbolForm || sym != NS) TODO("expected `ns` symbol") }

        z = z.zright ?: TODO("expected ns symbol")
        val ns = ((z.znode as? SymbolForm) ?: TODO("expected symbol")).sym

        var imports: Map<Symbol, TruffleObject>? = null
        var aliases: Map<Symbol, Symbol>? = null
        var refers: Map<Symbol, GlobalVar>? = null

        z.zright?.let {
            if (it.znode !is RecordForm) TODO("expected record")
            var optsZ = it.zdown
            while (optsZ != null) {
                val kw = (optsZ.znode as? KeywordForm)?.sym

                optsZ = optsZ.zright ?: TODO("missing form")
                when (kw) {
                    IMPORTS -> {
                        if (imports != null) TODO("duplicate imports")
                        imports = optsZ.analyseImports()
                    }

                    ALIASES -> {
                        if (aliases != null) TODO("duplicate aliases")
                        aliases = optsZ.analyseAliases()
                    }

                    REFERS -> {
                        if (refers != null) TODO("duplicate refers")
                        refers = optsZ.analyseRefers()
                    }

                    null -> TODO("expected keyword")
                    else -> TODO("unexpected keyword ${kw}")
                }

                optsZ = optsZ.zright
            }
        }

        return NsContext(
            env, ns,
            aliases = aliases ?: emptyMap(),
            imports = imports ?: emptyMap(),
            refers = refers ?: emptyMap()
        )
    }
}

internal fun Zip<Form>.analyseNs(ctx: BridjeContext) = NsAnalyser(ctx).run { analyseNs() }

internal fun Zip<Form>.isNsForm() =
    znode is ListForm && (zdown?.znode as? SymbolForm)?.sym == NS
