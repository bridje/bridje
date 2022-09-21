package brj

import brj.runtime.BridjeContext
import brj.runtime.NsContext
import brj.runtime.QSymbol
import brj.runtime.QSymbol.Companion.qsym
import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.interop.TruffleObject

private val NS = "ns".sym
private val IMPORTS = "imports".sym
private val REFERS = "refers".sym
private val ALIASES = "aliases".sym

internal data class ParsedNs(
    val ns: Symbol,
    val aliases: Map<Symbol, Symbol>,
    val refers: Map<Symbol, QSymbol>,
    val imports: Map<Symbol, Symbol>
)

private fun Zip<Form>.analyseImports(): Map<Symbol, Symbol> {
    val imports = mutableMapOf<Symbol, Symbol>()
    if (znode !is RecordForm) TODO("expected record")

    var importZ = zdown

    while (importZ != null) {
        val packageSym = (importZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")
        importZ = importZ.zright ?: TODO("missing form")
        if (importZ.znode !is SetForm) TODO("expected set")

        importZ.zdown.zrights.forEach {
            val sym = (it.znode as? SymbolForm)?.sym ?: TODO("expected sym")
            imports[sym] = "$packageSym.$sym".sym
        }

        importZ = importZ.zright
    }
    return imports
}

private fun Zip<Form>.analyseAliases(): Map<Symbol, Symbol> {
    val aliases = mutableMapOf<Symbol, Symbol>()
    if (znode !is RecordForm) TODO("expected record")

    var aliasZ = zdown

    while (aliasZ != null) {
        val aliasSym = (aliasZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")

        aliasZ = aliasZ.zright ?: TODO("missing form")
        val nsSym = ((aliasZ.znode as? SymbolForm) ?: TODO("expected symbol")).sym

        aliases[aliasSym] = nsSym

        aliasZ = aliasZ.zright
    }
    return aliases
}

private fun Zip<Form>.analyseRefers(): Map<Symbol, QSymbol> {
    val refers = mutableMapOf<Symbol, QSymbol>()
    if (znode !is RecordForm) TODO("expected record")

    var referZ = zdown

    while (referZ != null) {
        val nsSym = (referZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")

        referZ = referZ.zright ?: TODO("missing form")
        if (referZ.znode !is SetForm) TODO("expected set")

        referZ.zdown.zrights.forEach {
            val sym = (it.znode as? SymbolForm)?.sym ?: TODO("expected sym")
            refers[sym] = Pair(nsSym, sym).qsym
        }

        referZ = referZ.zright
    }

    return refers
}

internal fun Zip<Form>.analyseNs(): ParsedNs {
    if (znode !is ListForm) TODO("expected list")
    var z = zdown ?: TODO("expected `ns` symbol")
    z.znode.run { if (this !is SymbolForm || sym != NS) TODO("expected `ns` symbol") }

    z = z.zright ?: TODO("expected ns symbol")
    val ns = ((z.znode as? SymbolForm) ?: TODO("expected symbol")).sym

    var imports: Map<Symbol, Symbol>? = null
    var aliases: Map<Symbol, Symbol>? = null
    var refers: Map<Symbol, QSymbol>? = null

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
                else -> TODO("unexpected keyword $kw")
            }

            optsZ = optsZ.zright
        }
    }

    return ParsedNs(ns, aliases ?: emptyMap(), refers ?: emptyMap(), imports ?: emptyMap())
}

internal fun Zip<Form>.analyseNs(env: BridjeContext) = analyseNs().run {
    NsContext(
        env, ns,
        aliases.mapValues { env[it.value] ?: TODO("unknown ns") },
        refers.mapValues { (_, qSym) ->
            val nsContext = env[qSym.ns] ?: TODO("unknown ns")
            (nsContext.globalVars[qSym.local] ?: TODO("unknown var"))
        },
        imports.mapValues { (_, classFullSym) ->
            (((env.truffleEnv.lookupHostSymbol(classFullSym.name)) as? TruffleObject) ?: TODO("unknown class"))
        }
    )
}

internal fun Zip<Form>.isNsForm() =
    znode is ListForm && (zdown?.znode as? SymbolForm)?.sym == NS
