package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.analyser.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AnalyserTest {
    val dummyVar = object : Any() {}

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
            mkQSym("Foo/isZero") to JavaImport(Foo::class.java, mkQSym("Foo/isZero"), Type(FnType(listOf(IntType), BoolType), setOf()))),
            nsEnv.javaImports)
    }

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

    @Test
    fun `analyses record`() {
        val user = mkSym("user")
        val count = mkQSym(":user/count")
        val message = mkQSym(":user/message")

        val countKey = RecordKey(count, emptyList(), IntType)
        val messageKey = RecordKey(message, emptyList(), StringType)

        val nsEnv = NSEnv(user, vars = mapOf(
            count.base to RecordKeyVar(countKey, dummyVar),
            message.base to RecordKeyVar(messageKey, dummyVar)))

        assertEquals(
            DoExpr(emptyList(), RecordExpr(listOf(
                RecordEntry(countKey, IntExpr(42)),
                RecordEntry(messageKey, StringExpr("Hello world!"))))),

            analyseValueExpr(Env(mapOf(user to nsEnv)), nsEnv, readForms("""{:count 42, :message "Hello world!"}""")))
    }


    private val boolKey = VariantKey(mkQSym(":user/BooleanForm"), emptyList(), emptyList())
    private val strKey = VariantKey(mkQSym(":user/StringForm"), emptyList(), emptyList())

    private fun analyseMonoType(s: String): MonoType =
        TypeAnalyser(Env(), NSEnv(mkSym("user"),
            vars = mapOf(
                boolKey.sym.base to VariantKeyVar(boolKey, dummyVar),
                strKey.sym.base to VariantKeyVar(strKey, dummyVar))))
            .monoTypeAnalyser(ParserState(readForms(s)))

    @Test
    internal fun `analyses variant type declaration`() {
        val s = "(+ :BooleanForm :StringForm)"

        assertEquals(
            mapOf(
                boolKey to KeyType(boolKey, emptyList()),
                strKey to KeyType(strKey, emptyList())),
            (analyseMonoType(s) as VariantType).keyTypes)

    }
}

