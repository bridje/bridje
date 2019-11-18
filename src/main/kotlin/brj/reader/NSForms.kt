package brj.reader

import brj.analyser.NSHeader
import brj.analyser.NSHeader.Companion.nsHeaderParser
import brj.analyser.ParserState
import brj.reader.NSForms.Loader.Companion.ClasspathLoader
import brj.runtime.Symbol
import com.oracle.truffle.api.source.Source

internal data class NSForms(val nsHeader: NSHeader, val forms: List<Form>) {

    internal interface Loader {
        fun loadForms(ns: Symbol): List<Form>

        companion object {
            class ClasspathLoader(private val sources: Map<Symbol, Source> = emptyMap(),
                                  private val forms: Map<Symbol, List<Form>> = emptyMap()) : Loader {
                private fun nsSource(ns: Symbol): Source? =
                    this::class.java.getResource("/${ns.baseStr.replace('.', '/')}.brj")
                        ?.let { url -> Source.newBuilder("brj", url).build() }

                override fun loadForms(ns: Symbol): List<Form> =
                    forms[ns] ?: FormReader.readSourceForms(sources[ns] ?: nsSource(ns) ?: TODO("ns not found"))
            }
        }
    }

    companion object {
        fun loadNSes(rootNSes: Set<Symbol>, loader: Loader = ClasspathLoader()): List<NSForms> {
            val stack = mutableSetOf<Symbol>()

            val res = mutableListOf<NSForms>()
            val seen = mutableSetOf<Symbol>()

            fun loadNS(ns: Symbol) {
                if (seen.contains(ns)) return
                if (stack.contains(ns)) throw TODO("Cyclic NS")

                stack += ns
                seen += ns

                val state = ParserState(loader.loadForms(ns))
                val nsHeader = nsHeaderParser(state) ?: TODO()
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