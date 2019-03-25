package brj.analyser

import brj.*
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ValueExprAnalyserTest {
    val dummyVar = object : Any() {}

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

    @Test
    internal fun `analyses with-fx`() {
        val user = mkSym("user")
        val println = mkQSym("user/println!")

        val effectVar = EffectVar(println, Type(FnType(listOf(StringType), StringType), effects = setOf(println)), false, null)

        val nsEnv = NSEnv(user, vars = mapOf(println.base to effectVar))

        val expr = analyseValueExpr(Env(mapOf(user to nsEnv)), nsEnv, readForms("""(with-fx [(def (println! s) "Hello!")] (println! "foo!"))"""))
        val withFxExpr = (expr as DoExpr).expr as WithFxExpr

        println(withFxExpr)
    }
}