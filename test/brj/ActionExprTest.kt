package brj

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ActionExprTest {
    fun <R> analyse(a: (ActionExprAnalyser, AnalyserState) -> R, forms: List<Form>, env: Env = Env(), nsEnv: NSEnv = NSEnv(Symbol.mkSym("USER"))): R =
        a(ActionExprAnalyser(env, nsEnv), AnalyserState(forms))

    @Test
    internal fun testTypedef() {
        assertEquals(
            TypeDefExpr(Symbol.mkSym("foo"), Type(StringType, emptySet())),
            analyse(ActionExprAnalyser::typeDefAnalyser, readForms("foo Str")))

        assertEquals(
            TypeDefExpr(Symbol.mkSym("foo"), Type(FnType(listOf(StringType, IntType), StringType), emptySet())),
            analyse(ActionExprAnalyser::typeDefAnalyser, readForms("(foo Str Int) Str")))
    }
}