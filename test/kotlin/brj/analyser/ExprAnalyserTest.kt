package brj.analyser

import brj.*
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExprAnalyserTest {

    private val exprAnalyser = ExprAnalyser(Env(), NSEnv(mkSym("user")))

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
    }

    @Test
    fun `analyses type aliases`() {
        val foo = mkQSym("user/Foo")
        assertEquals(
            TypeAliasDeclExpr(TypeAlias(foo, emptyList(), Type(FnType(listOf(IntType), StringType)))),
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
        val fooVar = mkQSym("user/.foo")

        val polyDecl = analyseDecl("(.foo a) a")
        val typeVar = (polyDecl as PolyVarDeclExpr).typeVar

        assertEquals(
            PolyVarDeclExpr(fooVar, typeVar, Type(typeVar)),
            polyDecl)

        val polyDecl2 = analyseDecl("((.foo a) a) Int")
        val typeVar2 = (polyDecl2 as PolyVarDeclExpr).typeVar

        assertEquals(
            PolyVarDeclExpr(fooVar, typeVar2, Type(FnType(listOf(typeVar2), IntType))),
            polyDecl2)
    }

}

