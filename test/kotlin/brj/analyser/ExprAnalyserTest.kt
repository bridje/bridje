package brj.analyser

import brj.*
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.types.FnType
import brj.types.IntType
import brj.types.StringType
import brj.types.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExprAnalyserTest {

    private val exprAnalyser = ExprAnalyser(RuntimeEnv(), NSEnv(mkSym("user")))

    private fun analyseDecl(s: String) = exprAnalyser.analyseDecl(ParserState(readForms(s)))

    @Test
    fun `analyses var declarations`() {
        val foo = mkQSym("user/foo")

        assertEquals(
            VarDeclExpr(foo, Type(IntType)),
            analyseDecl("foo Int"))

        assertEquals(
            VarDeclExpr(foo, Type(FnType(listOf(IntType), StringType))),
            analyseDecl("(foo Int) Str"))

        assertEquals(
            VarDeclExpr(foo, Type(FnType(listOf(IntType), StringType), effects = setOf(foo))),
            analyseDecl("(! (foo Int)) Str"))
    }

    private fun analyseDef(s: String) = exprAnalyser.analyseDef(ParserState(readForms(s)))

    @Test
    internal fun `analyses var definitions`() {
        val foo = mkQSym("user/foo")

        assertEquals(
            DefExpr(foo,
                FnExpr(foo.base, emptyList(), DoExpr(emptyList(), IntExpr(4))),
                Type(FnType(emptyList(), IntType), emptyMap(), setOf(foo))),

            analyseDef("(! (foo)) 4"))
    }

    @Test
    fun `analyses type aliases`() {
        val foo = mkQSym("user/Foo")
        assertEquals(
            TypeAliasDeclExpr(TypeAlias_(foo, emptyList(), FnType(listOf(IntType), StringType))),
            analyseDecl("Foo (Fn Int Str)"))
    }

    @Test
    fun `analyses key decl`() {
        val fooKey = mkQSym(":user/foo")
        assertEquals(
            RecordKeyDeclExpr(RecordKey(fooKey, emptyList(), IntType)),
            analyseDecl(":foo Int"))

        val decl = analyseDecl("(:foo a) a")
        val typeVar = (decl as RecordKeyDeclExpr).recordKey.typeVars.first()

        assertEquals(
            RecordKeyDeclExpr(RecordKey(fooKey, listOf(typeVar), typeVar)),
            decl)


        val fooVariant = mkQSym(":user/Foo")

        assertEquals(
            VariantKeyDeclExpr(VariantKey(fooVariant, emptyList(), emptyList())),
            analyseDecl(":Foo"))

        assertEquals(
            VariantKeyDeclExpr(VariantKey(fooVariant, emptyList(), listOf(IntType, StringType))),
            analyseDecl(":Foo Int Str"))

    }

    @Test
    fun `analyses polyvar`() {
        val fooVar = mkQSym("user/foo")

        val polyDecl = analyseDecl("(. a) foo a")
        val typeVar = (polyDecl as PolyVarDeclExpr).polyVar.polyTypeVar

        val polyConstraints = mapOf(typeVar to setOf(fooVar))
        assertEquals(
            PolyVarDeclExpr(PolyVar(fooVar, typeVar, Type(typeVar, polyConstraints = polyConstraints))),
            polyDecl)

        val polyDecl2 = analyseDecl("(. a) (foo a) Int")
        val typeVar2 = (polyDecl2 as PolyVarDeclExpr).polyVar.polyTypeVar

        assertEquals(
            PolyVarDeclExpr(PolyVar(fooVar, typeVar2, Type(FnType(listOf(typeVar2), IntType), polyConstraints = polyConstraints))),
            polyDecl2)
    }

}

