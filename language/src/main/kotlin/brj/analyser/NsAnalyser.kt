package brj.analyser

import brj.*

data class NsDecl(
    val name: String,
    val imports: Imports = emptyMap()
)

private fun analyseClassSpec(packagePrefix: String, spec: Form): Pair<String, String> = 
    when (spec) {
        is SymbolForm -> {
            val className = spec.name
            className to "$packagePrefix:$className"
        }
        is ListForm -> {
            if ((spec.els.firstOrNull() as? SymbolForm)?.name != "as") {
                error("class spec list must be an 'as' form: $spec")
            }
            val className = (spec.els.getOrNull(1) as? SymbolForm)?.name
                ?: error("as requires class name: $spec")
            val alias = (spec.els.getOrNull(2) as? SymbolForm)?.name
                ?: error("as requires alias: $spec")
            alias to "$packagePrefix:$className"
        }
        else -> error("invalid class spec: $spec")
    }

private fun analyseImport(importForm: ListForm): Map<String, String> {
    val imports = mutableMapOf<String, String>()

    for (packageForm in importForm.els.drop(1)) {
        if (packageForm !is ListForm) error("import package must be a list: $packageForm")

        val packagePrefix = (packageForm.els.firstOrNull() as? SymbolForm)?.name
            ?: error("import package must start with package name: $packageForm")

        for (classSpec in packageForm.els.drop(1)) {
            val (alias, fqClass) = analyseClassSpec(packagePrefix, classSpec)
            imports[alias] = fqClass
        }
    }

    return imports
}

fun List<Form>.analyseNs(): Pair<NsDecl?, List<Form>> {
    val first = firstOrNull()
    if (first !is ListForm) return Pair(null, this)

    val els = first.els
    if ((els.firstOrNull() as? SymbolForm)?.name != "ns") return Pair(null, this)

    val nsName = (els.getOrNull(1) as? SymbolForm)?.name
        ?: error("ns requires a name")

    var imports = emptyMap<String, String>()

    for (clause in els.drop(2)) {
        if (clause !is ListForm) error("ns clause must be a list: $clause")
        val clauseName = (clause.els.firstOrNull() as? SymbolForm)?.name
            ?: error("ns clause must start with a symbol: $clause")

        when (clauseName) {
            "import" -> imports = analyseImport(clause)
            else -> error("Unknown ns clause: $clauseName")
        }
    }

    return Pair(NsDecl(nsName, imports), drop(1))
}
