package brj

import brj.ActionExprAnalyser.TypeDefExpr
import brj.BrjEnv.NSEnv
import brj.Form.Companion.readForms
import brj.Types.MonoType.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ActionExprTest {
    fun <R> analyse(a: (ActionExprAnalyser) -> FormsAnalyser<R>, forms: List<Form>, brjEnv: BrjEnv = BrjEnv(), nsEnv: NSEnv = NSEnv(Symbol.create("USER"))): R =
        a(ActionExprAnalyser(brjEnv, nsEnv))(Analyser.AnalyserState(forms))

    @Test
    internal fun testTypedef() {
        assertEquals(
            TypeDefExpr(Symbol.create("foo"), Types.Typing(StringType)),
            analyse(ActionExprAnalyser::typeDefAnalyser, readForms("foo Str")))

        assertEquals(
            TypeDefExpr(Symbol.create("foo"), Types.Typing(FnType(listOf(StringType, IntType), StringType))),
            analyse(ActionExprAnalyser::typeDefAnalyser, readForms("(foo Str Int) Str")))
    }
}