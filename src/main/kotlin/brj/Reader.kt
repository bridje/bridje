package brj

import java.util.*

internal data class NSFile(val nsEnv: NSEnv, val forms: List<Form>)

internal fun loadNSes(rootNSes: Set<Symbol>, nsForms: (Symbol) -> List<Form>): List<NSFile> {
    val stack = LinkedHashSet<Symbol>()

    val res = LinkedList<NSFile>()
    val seen = mutableSetOf<Symbol>()

    fun loadNS(ns: Symbol) {
        if (seen.contains(ns)) return
        if (stack.contains(ns)) throw TODO("Cyclic NS")

        stack += ns

        val state = AnalyserState(nsForms(ns))
        val nsEnv = NSAnalyser(ns).analyseNS(state)

        (nsEnv.deps - seen).forEach(::loadNS)

        res.add(NSFile(nsEnv, state.forms))

        stack -= ns
    }

    rootNSes.forEach(::loadNS)

    return res
}
