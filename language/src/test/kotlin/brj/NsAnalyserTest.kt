package brj

import brj.runtime.QSymbol.Companion.qsym
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class NsAnalyserTest {
    fun String.analyseNs() =
        readForms(Source.newBuilder("brj", this, "<ns-analyser-test>").build())
            .first().zip.analyseNs()

    @Test
    internal fun `test ns analyser`() {
        assertEquals(
            ParsedNs(
                "foo".sym,
                aliases = mapOf("brj".sym to "brj.core".sym),
                refers = mapOf(
                    "inc".sym to "brj.core/inc".qsym,
                    "dec".sym to "brj.core/dec".qsym
                ),
                imports = mapOf(
                    "Duration".sym to "java.time.Duration".sym,
                    "Period".sym to "java.time.Period".sym
                )
            ),
            "(ns foo {:aliases {brj brj.core}, :refers {brj.core #{inc dec}}, :imports {java.time #{Duration Period}}})".analyseNs()
        )

    }
}