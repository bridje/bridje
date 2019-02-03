package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AnalyserTest {
    fun analyseNS(s: String): NSEnv = NSAnalyser(Symbol.mkSym("foo")).analyseNS(AnalyserState(readForms(s)))

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

    private val actionExprAnalyser = ActionExprAnalyser(Env(), NSEnv(mkSym("USER")))

    private fun analyseDecl(s: String) = actionExprAnalyser.declAnalyser(AnalyserState(readForms(s)))

    @Test
    fun `analyses var declarations`() {
        val foo = mkSym("foo")

        assertEquals(
            VarDeclExpr(foo, Type(IntType)),
            analyseDecl("foo Int"))

        assertEquals(
            VarDeclExpr(foo, Type(FnType(listOf(IntType), StringType))),
            analyseDecl("(foo Int) Str"))
    }

    @Test
    fun `analyses type aliases`() {
        val foo = mkSym("Foo")
        assertEquals(
            TypeAliasDeclExpr(foo, null, Type(FnType(listOf(IntType), StringType))),
            analyseDecl("Foo (Fn Int Str)"))
    }

    @Test
    fun `analyses key decl`() {
        val fooKey = mkSym(":foo")
        assertEquals(
            KeyDeclExpr(fooKey, null, Type(IntType)),
            analyseDecl(":foo Int"))

        val decl = analyseDecl("(:foo a) a")
        val typeVar = (decl as KeyDeclExpr).typeVars!!.first()

        assertEquals(
            KeyDeclExpr(fooKey, listOf(typeVar), Type(typeVar)),
            decl)


        val fooVariant = mkSym(":Foo")

        assertEquals(
            VariantDeclExpr(fooVariant, null, emptyList()),
            analyseDecl(":Foo"))

        assertEquals(
            VariantDeclExpr(fooVariant, null, listOf(Type(IntType), Type(StringType))),
            analyseDecl(":Foo Int Str"))

    }
}

