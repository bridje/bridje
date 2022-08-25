package brj

import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Test

internal class NsAnalyserTest {

    fun String.analyseNs() =
        readForms(Source.newBuilder("brj", this, "<ns-analyser-test>").build())
            .first().zip.analyseNs()

    @Test
    internal fun `test ns analyser`() {
        println("(ns foo {:aliases {brj brj.core}, :refers {brj.core #{inc dec}}, :imports {java.time #{Duration Period}}})".analyseNs())
    }
}