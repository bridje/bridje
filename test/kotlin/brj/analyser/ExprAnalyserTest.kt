package brj.analyser

import brj.emitter.Symbol.Companion.mkSym
import brj.readForms
import brj.runtime.NSEnv
import brj.types.FnType
import brj.types.IntType
import brj.types.StringType
import brj.types.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExprAnalyserTest {

    private val exprAnalyser = ExprAnalyser(NSEnv(mkSym("user")))

    private fun analyseDecl(s: String) = exprAnalyser.declAnalyser(ParserState(readForms(s)))

    @Test
    fun `analyses var declarations`() {
        val foo = mkSym("foo")

        assertEquals(
            VarDeclExpr(foo, false, Type(IntType)),
            analyseDecl("(:: foo Int)"))

        assertEquals(
            VarDeclExpr(foo, false, Type(FnType(listOf(IntType), StringType))),
            analyseDecl("(:: (foo Int) Str)"))

        assertEquals(
            VarDeclExpr(foo, true, Type(FnType(listOf(IntType), StringType))),
            analyseDecl("(:: (! (foo Int)) Str)"))
    }

    private fun analyseDef(s: String) = exprAnalyser.defAnalyser(ParserState(readForms(s)))

    @Test
    internal fun `analyses var definitions`() {
        val foo = mkSym("foo")

        assertEquals(
            DefExpr(foo,
                FnExpr(foo, emptyList(), DoExpr(emptyList(), IntExpr(4))),
                true,
                Type(FnType(emptyList(), IntType), emptyMap(), emptySet())),

            analyseDef("(def (! (foo)) 4)"))
    }

    @Test
    fun `analyses type aliases`() {
        val foo = mkSym("Foo")
        assertEquals(
            TypeAliasDeclExpr(foo, emptyList(), FnType(listOf(IntType), StringType)),
            analyseDecl("(:: Foo (Fn Int Str))"))
    }

    @Test
    fun `analyses key decl`() {
        val fooKey = mkSym(":foo")
        assertEquals(
            RecordKeyDeclExpr(fooKey, emptyList(), IntType),
            analyseDecl("(:: :foo Int)"))

        val decl = analyseDecl("(:: (:foo a) a)")
        val typeVar = (decl as RecordKeyDeclExpr).typeVars.first()

        assertEquals(
            RecordKeyDeclExpr(fooKey, listOf(typeVar), typeVar),
            decl)


        val fooVariant = mkSym(":Foo")

        assertEquals(
            VariantKeyDeclExpr(fooVariant, emptyList(), emptyList()),
            analyseDecl("(:: :Foo)"))

        assertEquals(
            VariantKeyDeclExpr(fooVariant, emptyList(), listOf(IntType, StringType)),
            analyseDecl("(:: :Foo Int Str)"))

    }

    @Test
    fun `analyses polyvar`() {
        val fooVar = mkSym("foo")

        val polyDecl = analyseDecl("(:: (. a) foo a)")
        val typeVar = (polyDecl as PolyVarDeclExpr).polyTypeVar

        assertEquals(
            PolyVarDeclExpr(fooVar, typeVar, typeVar),
            polyDecl)

        val polyDecl2 = analyseDecl("(:: (. a) (foo a) Int)")
        val typeVar2 = (polyDecl2 as PolyVarDeclExpr).polyTypeVar

        assertEquals(
            PolyVarDeclExpr(fooVar, typeVar2, FnType(listOf(typeVar2), IntType)),
            polyDecl2)
    }

}

