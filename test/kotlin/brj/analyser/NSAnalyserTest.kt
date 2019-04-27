package brj.analyser

import brj.BrjLanguage
import brj.JavaImport
import brj.NSEnv
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.readForms
import brj.types.BoolType
import brj.types.FnType
import brj.types.IntType
import brj.types.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NSAnalyserTest {
    fun analyseNS(s: String): NSEnv = NSAnalyser(mkSym("foo")).analyseNS(readForms(s).first())

    @Test
    fun `analyses refers`() {
        val nsEnv = analyseNS("(ns foo {:refers {bar #{baz :Ok}}})")

        assertEquals(
            mapOf(
                mkSym("baz") to mkQSym("bar/baz"),
                mkSym(":Ok") to mkQSym(":bar/Ok")),
            nsEnv.refers)
    }

    @Test
    fun `analyses Java imports`() {
        val nsEnv = analyseNS("(ns foo {:imports {Foo (java brj.Foo (:: (isZero Int) Bool))}})")
        assertEquals(mapOf(
            mkQSym("Foo/isZero") to JavaImport(BrjLanguage::class.java, mkQSym("Foo/isZero"), Type(FnType(listOf(IntType), BoolType), setOf()))),
            nsEnv.javaImports)
    }
}