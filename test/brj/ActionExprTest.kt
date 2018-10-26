package brj

import brj.Form.Companion.readForms
import org.junit.jupiter.api.Test

internal class ActionExprTest {
    fun analyseActionExpr(brjEnv: BrjEnv = BrjEnv(), ns: Symbol = Symbol.create("USER"), form: Form): ActionExpr {
        return ActionExpr.ActionExprAnalyser(brjEnv, ns).actionExprAnalyser(Analyser.AnalyserState(listOf(form)))
    }

    @Test
    internal fun testTypedef() {
        println(readForms("(:: foo Str)"))
        println(analyseActionExpr(form = readForms("(:: foo Str)").first()))
    }
}