package brj.analyser

import brj.runtime.*
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.RECORD
import brj.types.FnType
import brj.types.IntType
import brj.types.StringType
import brj.types.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ValueExprAnalyserTest {
    val dummyVar = object : Any() {}

    @Test
    fun `analyses record`() {
        val user = Symbol(ID, "user")
        val count = QSymbol(user, Symbol(RECORD, "count"))
        val message = QSymbol(user, Symbol(RECORD, "message"))

        val countKey = RecordKey(count, emptyList(), IntType)
        val messageKey = RecordKey(message, emptyList(), StringType)

        val nsEnv = NSEnv(user, vars = mapOf(
            count.local to RecordKeyVar(countKey, dummyVar),
            message.local to RecordKeyVar(messageKey, dummyVar)))

        assertEquals(
            DoExpr(emptyList(), RecordExpr(listOf(
                RecordEntry(countKey, IntExpr(42)),
                RecordEntry(messageKey, StringExpr("Hello world!"))))),

            ValueExprAnalyser(Resolver.NSResolver(nsEnv = nsEnv)).analyseValueExpr(TODO() /*readForms("""{:count 42, :message "Hello world!"}""").first()*/))
    }

    @Test
    internal fun `analyses with-fx`() {
        val user = Symbol(ID, "user")
        val println = QSymbol(user, Symbol(ID, "println!"))

        val effectVar = EffectVar(println, Type(FnType(listOf(StringType), StringType)), defaultImpl = null, value = "ohno")

        val nsEnv = NSEnv(user, vars = mapOf(println.local to effectVar))

        val expr = ValueExprAnalyser(Resolver.NSResolver(nsEnv = nsEnv))
            .analyseValueExpr(TODO() /*readForms("""(with-fx [(def (println! s) "Hello!")] (println! "foo!"))""").first()*/)

        val withFxExpr = (expr as DoExpr).expr as WithFxExpr

        assertEquals(1, withFxExpr.fx.size)
        val effect = withFxExpr.fx.first()

        assertEquals(println, effect.effectVar.sym)

        assertEquals(
            DoExpr(emptyList(), StringExpr("Hello!")),
            effect.fnExpr.expr)

        val effectLocal = LocalVar(Symbol(ID, "fx"))

        assertEquals(
            DoExpr(emptyList(), CallExpr(GlobalVarExpr(effectVar, effectLocal), listOf(StringExpr("foo!")), effectLocal)),
            withFxExpr.bodyExpr)
    }
}