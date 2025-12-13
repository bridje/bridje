package brj

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InteropTest {
    @Test
    fun `calls java static method via qualified symbol`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val result = ctx.eval("bridje", "java:time:Instant/now()")
                    assertTrue(result.isInstant, "Result should be an Instant")
                } finally {
                    ctx.leave()
                }
            }
    }
}
