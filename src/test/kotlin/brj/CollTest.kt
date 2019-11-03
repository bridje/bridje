package brj

import org.junit.jupiter.api.Test

internal class CollTest {
    @Test
    fun `e2e coll test`() {
        withCtx {ctx ->
            println(ctx.eval("brj", """#{1 2 3}""").hasArrayElements())
        }
    }
}