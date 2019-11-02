package brj

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Test

internal class FxTest {
    @Test
    fun `e2e fx test`() {
        println(Context.newBuilder().allowAllAccess(true).build().also { it.enter() }.eval("brj", """
                    (fn [x] x)
                """.trimIndent()))
    }
}