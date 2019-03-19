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


}