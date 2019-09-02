package brj

import brj.analyser.NSHeader
import brj.analyser.ParserState

internal data class NSForms(val nsHeader: NSHeader, val forms: List<Form>) {

    internal interface Loader {
        fun loadForms(ns: Symbol): List<Form>
    }

    companion object {
        fun loadNSes(rootNSes: Set<Symbol>, loader: Loader): List<NSForms> {
            val stack = mutableSetOf<Symbol>()

            val res = mutableListOf<NSForms>()
            val seen = mutableSetOf<Symbol>()

            fun loadNS(ns: Symbol) {
                if (seen.contains(ns)) return
                if (stack.contains(ns)) throw TODO("Cyclic NS")

                stack += ns
                seen += ns

                val state = ParserState(loader.loadForms(ns))
                val nsHeader = NSHeader.nsHeaderParser(state) ?: TODO()
                nsHeader.ns == ns || TODO()

                (nsHeader.deps - seen).forEach(::loadNS)

                res.add(NSForms(nsHeader, state.forms))

                stack -= ns
            }

            rootNSes.forEach(::loadNS)

            return res
        }
    }
}