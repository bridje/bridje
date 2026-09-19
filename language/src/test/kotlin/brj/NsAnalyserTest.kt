package brj

import brj.Reader.Companion.readForms
import brj.analyser.analyseNs
import brj.runtime.sym
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NsAnalyserTest {
    private fun String.parseNs() =
        Source.newBuilder("bridje", this, "test.brj").build()
            .readForms().toList().analyseNs()

    @Test
    fun `parses imports`() {
        val (nsDecl, _) = """
            ns: foo.bar
              import:
                java.time:
                  as(Instant, Inst)
                  as(Duration, Dur)
                java.util:
                  Map List Set
        """.trimIndent().parseNs()

        assertEquals("foo.bar".sym, nsDecl?.name)
        assertEquals(
            mapOf(
                "Inst".sym to "java.time.Instant",
                "Dur".sym to "java.time.Duration",
                "Map".sym to "java.util.Map",
                "List".sym to "java.util.List",
                "Set".sym to "java.util.Set"
            ),
            nsDecl?.imports
        )
    }

    @Test
    fun `parses s-expression ns form`() {
        val (nsDecl, _) = "(ns foo.bar (require (java.time (as Instant Inst))))".parseNs()

        assertEquals("foo.bar".sym, nsDecl?.name)
        assertEquals(mapOf("Inst".sym to "java.time.Instant".sym), nsDecl?.requires)
    }

    @Test
    fun `parses requires`() {
        val (nsDecl, _) = """
            ns: foo.bar
              require:
                other:
                  as(lib, lib)
                  util
        """.trimIndent().parseNs()

        assertEquals("foo.bar".sym, nsDecl?.name)
        assertEquals(
            mapOf(
                "lib".sym to "other.lib".sym,
                "util".sym to "other.util".sym
            ),
            nsDecl?.requires
        )
    }
}
