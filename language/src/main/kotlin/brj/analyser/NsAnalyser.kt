package brj.analyser

import brj.*

data class NsDecl(
    val name: String,
    val requires: Map<String, String> = emptyMap(),
    val imports: Imports = emptyMap()
)

private fun analyseSpec(prefix: String, spec: Form): Pair<String, String> =
    when (spec) {
        is SymbolForm -> {
            val name = spec.name
            name to "$prefix:$name"
        }
        is ListForm -> {
            if ((spec.els.firstOrNull() as? SymbolForm)?.name != "as") {
                error("spec list must be an 'as' form: $spec")
            }
            val name = (spec.els.getOrNull(1) as? SymbolForm)?.name
                ?: error("as requires name: $spec")
            val alias = (spec.els.getOrNull(2) as? SymbolForm)?.name
                ?: error("as requires alias: $spec")
            alias to "$prefix:$name"
        }
        else -> error("invalid spec: $spec")
    }

private fun analysePackagedClause(clauseForm: ListForm): Map<String, String> {
    val result = mutableMapOf<String, String>()

    for (packageForm in clauseForm.els.drop(1)) {
        if (packageForm !is ListForm) error("package group must be a list: $packageForm")

        val prefix = (packageForm.els.firstOrNull() as? SymbolForm)?.name
            ?: error("package group must start with package name: $packageForm")

        for (spec in packageForm.els.drop(1)) {
            val (alias, fqName) = analyseSpec(prefix, spec)
            result[alias] = fqName
        }
    }

    return result
}

fun List<Form>.analyseNs(): Pair<NsDecl?, List<Form>> {
    val first = firstOrNull()
    if (first !is ListForm) return Pair(null, this)

    val els = first.els
    if ((els.firstOrNull() as? SymbolForm)?.name != "ns") return Pair(null, this)

    val nsName = (els.getOrNull(1) as? SymbolForm)?.name
        ?: error("ns requires a name")

    var requires = emptyMap<String, String>()
    var imports = emptyMap<String, String>()

    for (clause in els.drop(2)) {
        if (clause !is ListForm) error("ns clause must be a list: $clause")
        val clauseName = (clause.els.firstOrNull() as? SymbolForm)?.name
            ?: error("ns clause must start with a symbol: $clause")

        when (clauseName) {
            "require" -> requires = analysePackagedClause(clause)
            "import" -> imports = analysePackagedClause(clause)
            else -> error("Unknown ns clause: $clauseName")
        }
    }

    return Pair(NsDecl(nsName, requires, imports), drop(1))
}
