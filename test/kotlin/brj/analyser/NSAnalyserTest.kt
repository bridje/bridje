package brj.analyser

import brj.NSEnv
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.readForms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NSAnalyserTest {
    private val ns = mkSym("foo")

    fun analyseNS(s: String): NSEnv =
        NSAnalyser(ns).analyseNS(readForms(s).first())

    @Test
    fun `analyses refers`() {
        val nsEnv = analyseNS("(ns foo {:refers {bar #{baz :Ok}}})")

        assertEquals(
            mapOf(
                mkSym("baz") to mkQSym("bar/baz"),
                mkSym(":Ok") to mkQSym(":bar/Ok")),
            nsEnv.refers)
    }
}