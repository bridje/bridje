package brj

import brj.Reader.Companion.readForms
import brj.analyser.analyseNs
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
            ns: foo:bar
              import:
                java:time:
                  Instant.as(Inst)
                  Duration.as(Dur)
                java:util:
                  Map List Set
        """.trimIndent().parseNs()

        assertEquals("foo:bar", nsDecl?.name)
        assertEquals(
            mapOf(
                "Inst" to "java:time:Instant",
                "Dur" to "java:time:Duration",
                "Map" to "java:util:Map",
                "List" to "java:util:List",
                "Set" to "java:util:Set"
            ),
            nsDecl?.imports
        )
    }

    @Test
    fun `parses requires`() {
        val (nsDecl, _) = """
            ns: foo:bar
              require:
                other:
                  lib.as(lib)
                  util
        """.trimIndent().parseNs()

        assertEquals("foo:bar", nsDecl?.name)
        assertEquals(
            mapOf(
                "lib" to "other:lib",
                "util" to "other:util"
            ),
            nsDecl?.requires
        )
    }
}
