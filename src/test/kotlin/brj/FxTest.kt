package brj

import org.junit.jupiter.api.Test

internal class FxTest {
    @Test
    fun `e2e fx test`() {
        withCtx {ctx ->
            println(ctx.eval("brj", """[1 2]"""))
        }
    }
}