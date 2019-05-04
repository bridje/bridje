package brj.analyser

import brj.*
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.types.BoolType
import brj.types.FnType
import brj.types.IntType
import brj.types.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NSAnalyserTest {
    private val ns = mkSym("foo")

    fun analyseNS(s: String): NSEnv =
        NSAnalyser(ns, object : DeclAnalyser {
            override fun analyseDecl(state: ParserState): VarDeclExpr? =
                ExprAnalyser(RuntimeEnv(), NSEnv(ns), dummyMacroEvaluator()).analyseDecl(state) as VarDeclExpr?
        }).analyseNS(readForms(s).first())

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
        val nsEnv = analyseNS("(ns foo {:imports {Foo (java brj.FooKt (:: (isZero Int) Bool))}})")
        assertEquals(mapOf(
            mkSym("Foo.isZero") to JavaImport(mkQSym("foo/Foo.isZero"), Class.forName("brj.FooKt"), "isZero", Type(FnType(listOf(IntType), BoolType), setOf()))),
            nsEnv.javaImports)
    }
}