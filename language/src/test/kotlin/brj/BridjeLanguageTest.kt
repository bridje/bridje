package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.TypeLiteral
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
class BridjeLanguageTest {
    lateinit var ctx: Context

    @BeforeEach
    fun setUp() {
        ctx = Context.newBuilder().allowAllAccess(true).build()
        ctx.enter()
    }

    @AfterEach
    fun tearDown() {
        ctx.leave()
        ctx.close()
    }

    @Test
    internal fun `just a number`() {
        assertEquals(10, ctx.eval("brj", "10").asInt())
    }

    @Test
    internal fun `e2e vector test`() {
        val value = ctx.eval("brj", "(let [x 4, y 10] [x y])")
        assertEquals(listOf(4, 10), value.`as`(object : TypeLiteral<List<Int>>() {}))
    }

    @Test
    internal fun `exposes value in scope`() {
        ctx.eval("brj", "(def x 10)")
        ctx.eval("brj", """(def y "Hello")""")
        val bindings = ctx.getBindings("brj")
        assertEquals(setOf("x", "y"), bindings.memberKeys)
        assertEquals(10, bindings.getMember("x").asInt())
        assertEquals("Hello", bindings.getMember("y").asString())
        assertEquals(10, ctx.eval("brj", "x").asInt())
    }

    @Test
    internal fun `basic fn expr`() {
        ctx.eval("brj", "(def simple-vec (fn [x] [x]))")
        val bindings = ctx.getBindings("brj")
        assertEquals(listOf(10),
            bindings.getMember("simple-vec")
                .execute(10)
                .`as`(object : TypeLiteral<List<Int>>() {})
        )
    }
}