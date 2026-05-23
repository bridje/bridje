package brj.analyser

import brj.*
import brj.runtime.Symbol
import brj.runtime.sym

private fun analyseSpec(prefix: String, spec: Form): Pair<Symbol, Symbol> =
    when (spec) {
        is SymbolForm -> {
            val name = spec.sym.name
            spec.sym to "$prefix.$name".sym
        }
        is ListForm -> {
            if ((spec.els.firstOrNull() as? SymbolForm)?.sym?.name != "as") {
                error("spec list must be an 'as' form: $spec")
            }
            val name = (spec.els.getOrNull(1) as? SymbolForm)?.sym?.name
                ?: error("as requires name: $spec")
            val alias = (spec.els.getOrNull(2) as? SymbolForm)?.sym
                ?: error("as requires alias: $spec")
            alias to "$prefix.$name".sym
        }
        else -> error("invalid spec: $spec")
    }

private fun analyseRequires(clauseForm: ListForm): Map<Symbol, Symbol> {
    val result = mutableMapOf<Symbol, Symbol>()

    for (packageForm in clauseForm.els.drop(1)) {
        if (packageForm !is ListForm) error("package group must be a list: $packageForm")

        val prefix = (packageForm.els.firstOrNull() as? SymbolForm)?.sym?.name
            ?: error("package group must start with package name: $packageForm")

        for (spec in packageForm.els.drop(1)) {
            val (alias, fqName) = analyseSpec(prefix, spec)
            result[alias] = fqName
        }
    }

    return result
}

private fun analyseImports(clauseForm: ListForm): Map<Symbol, String> {
    val result = mutableMapOf<Symbol, String>()

    for (packageForm in clauseForm.els.drop(1)) {
        if (packageForm !is ListForm) error("package group must be a list: $packageForm")

        val prefix = (packageForm.els.firstOrNull() as? SymbolForm)?.sym?.name
            ?: error("package group must start with package name: $packageForm")

        for (spec in packageForm.els.drop(1)) {
            val (alias, fqName) = analyseSpec(prefix, spec)
            result[alias] = fqName.name
        }
    }

    return result
}

fun List<Form>.analyseNs(): Pair<NsDecl?, List<Form>> {
    val first = firstOrNull()
    if (first !is ListForm) return Pair(null, this)

    val els = first.els
    if ((els.firstOrNull() as? SymbolForm)?.sym?.name != "ns") return Pair(null, this)

    val nsName = when (val nameForm = els.getOrNull(1)) {
        is SymbolForm -> nameForm.sym
        is QSymbolForm -> "${nameForm.ns.name}.${nameForm.member.name}".sym
        else -> error("ns requires a name")
    }

    var requires = emptyMap<Symbol, Symbol>()
    var imports = emptyMap<Symbol, String>()

    for (clause in els.drop(2)) {
        if (clause !is ListForm) error("ns clause must be a list: $clause")
        val clauseName = (clause.els.firstOrNull() as? SymbolForm)?.sym?.name
            ?: error("ns clause must start with a symbol: $clause")

        when (clauseName) {
            "require" -> requires = analyseRequires(clause)
            "import" -> imports = analyseImports(clause)
            else -> error("Unknown ns clause: $clauseName")
        }
    }

    return Pair(NsDecl(nsName, requires, imports), drop(1))
}
