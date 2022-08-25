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

internal data class ParsedNs(
    val ns: Symbol,
    val aliases: Map<Symbol, Symbol>?,
    val refers: Map<Symbol, Set<Symbol>>?,
    val imports: Map<Symbol, Set<Symbol>>?
)

private fun Zip<Form>.analyseImports(): Map<Symbol, Set<Symbol>> {
    val imports = mutableMapOf<Symbol, Set<Symbol>>()
    if (znode !is RecordForm) TODO("expected record")

    var importZ = zdown

    while (importZ != null) {
        val packageSym = (importZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")
        importZ = importZ.zright ?: TODO("missing form")
        if (importZ.znode !is SetForm) TODO("expected set")

        imports[packageSym] = importZ.zdown.zrights.map {
            (it.znode as? SymbolForm)?.sym ?: TODO("expected sym")
        }.toSet()

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

private fun Zip<Form>.analyseRefers(): Map<Symbol, Set<Symbol>> {
    val refers = mutableMapOf<Symbol, Set<Symbol>>()
    if (znode !is RecordForm) TODO("expected record")

    var referZ = zdown

    while (referZ != null) {
        val nsSym = (referZ.znode as? SymbolForm)?.sym ?: TODO("expected symbol")

        referZ = referZ.zright ?: TODO("missing form")
        if (referZ.znode !is SetForm) TODO("expected set")

        refers[nsSym] = referZ.zdown.zrights.map {
            (it.znode as? SymbolForm)?.sym ?: TODO("expected sym")
        }.toSet()

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

    var imports: Map<Symbol, Set<Symbol>>? = null
    var aliases: Map<Symbol, Symbol>? = null
    var refers: Map<Symbol, Set<Symbol>>? = null

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

    return ParsedNs(ns, aliases, refers, imports)
}

internal fun Zip<Form>.analyseNs(env: BridjeContext) = analyseNs().run {
    NsContext(
        env, ns,
        aliases?.mapValues {env[it.key] ?: TODO("unknown ns")} ?: emptyMap(),
        refers?.flatMap { (nsSym, localSyms) ->
            localSyms.map { localSym ->
                val nsContext = env[nsSym] ?: TODO("unknown ns")
                localSym to (nsContext.globalVars[localSym] ?: TODO("unknown var"))
            }
        }?.toMap() ?: emptyMap(),
        imports?.flatMap { (packageSym, classSyms) ->
            classSyms.map { classSym ->
                val fullName = "$packageSym.$classSym"
                classSym to (((env.truffleEnv.lookupHostSymbol(fullName)) as? TruffleObject) ?: TODO("unknown class"))
            }
        }?.toMap() ?: emptyMap()
    )
}

internal fun Zip<Form>.isNsForm() =
    znode is ListForm && (zdown?.znode as? SymbolForm)?.sym == NS
