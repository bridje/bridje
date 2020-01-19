package brj

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class CollTest {

    @Test
    fun `e2e coll test`() {
        withCtx { ctx ->
            assertEquals(listOf(listOf(1L, 2L)), ctx.eval("brj", """[[1 2]]""").`as`(List::class.java))
        }
    }
}