package brj

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
class BridjeLanguageTest {
    lateinit var ctx: Context

    @BeforeAll
    fun setUp() {
        ctx = Context.newBuilder().allowAllAccess(true).build()
        ctx.enter()
    }

    @AfterAll
    fun tearDown() {
        ctx.leave()
        ctx.close()
    }

    @Test
    internal fun `e2e vector test`() {
        val value = ctx.eval("brj", "(let [x 4, y 10] [x y])")
        println(value)
    }

    @Test
    internal fun `exposes value in scope`() {
        ctx.initialize("brj")
        ctx.eval("brj", "(def x 10)")
        ctx.eval("brj", """(def y "Hello")""")
        val bindings = ctx.getBindings("brj")
        assertEquals(setOf("x", "y"), bindings.memberKeys)
        assertEquals(10, bindings.getMember("x").asInt())
        assertEquals("Hello", bindings.getMember("y").asString())
    }
}