package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import kotlin.test.Test
import kotlin.test.assertEquals

class NSAnalyserTest {
    fun analyseNS(s: String): NSEnv = nsAnalyser(AnalyserState(readForms(s)), Symbol.mkSym("foo"))

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
            mkQSym("Foo/isZero") to JavaImport(Foo::class.java, mkQSym("Foo/isZero"), Type(FnType(listOf(IntType), BoolType), setOf()))),
            nsEnv.javaImports)
    }
}

