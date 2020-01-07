package brj.reader

import brj.analyser.NSHeader
import brj.analyser.NSHeader.Companion.nsHeaderParser
import brj.analyser.ParserState
import brj.runtime.Symbol

internal interface FormLoader {
    fun loadForms(ns: Symbol): List<Form>

}

internal data class NSForms(val nsHeader: NSHeader, val forms: List<Form>)

internal fun nsForms(ns: Symbol, loader: FormLoader): List<NSForms> {
    val stack = mutableSetOf<Symbol>()

    val res = mutableListOf<NSForms>()
    val seen = mutableSetOf(FORM_NS)

    fun nsForms(ns: Symbol) {
        if (seen.contains(ns)) return
        if (stack.contains(ns)) throw TODO("Cyclic NS")

        stack += ns
        seen += ns

        val state = ParserState(loader.loadForms(ns))
        val nsHeader = nsHeaderParser(state) ?: TODO()
        nsHeader.ns == ns || TODO()

        (nsHeader.deps - seen).forEach(::nsForms)

        res.add(NSForms(nsHeader, state.forms))

        stack -= ns
    }

    nsForms(ns)

    return res
}
