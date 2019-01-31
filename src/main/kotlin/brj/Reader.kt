package brj

import java.util.*

internal data class NSFile(val nsEnv: NSEnv, val forms: List<Form>)

internal abstract class FormReader {
    abstract fun readForms(ns: Symbol): List<Form>

    internal fun readNSes(rootNSes: Set<Symbol>): List<NSFile> {
        val stack = LinkedHashSet<Symbol>()

        val res = LinkedList<NSFile>()
        val seen = mutableSetOf<Symbol>()

        fun readNS(ns: Symbol) {
            if (seen.contains(ns)) return
            if (stack.contains(ns)) throw TODO("Cyclic NS")

            stack += ns

            val state = AnalyserState(readForms(ns))
            val nsEnv = NSAnalyser(ns).analyseNS(state)

            (nsEnv.deps - seen).forEach(::readNS)

            res.add(NSFile(nsEnv, state.forms))

            stack -= ns
        }

        rootNSes.forEach(::readNS)

        return res
    }
}
