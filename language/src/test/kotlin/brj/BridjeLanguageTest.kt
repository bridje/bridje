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
    fun `just a number`() {
        assertEquals(10, ctx.eval("brj", "10").asInt())
    }

    private val listOfInt = object : TypeLiteral<List<Int>>() {}

    @Test
    fun `vector test`() {
        val value = ctx.eval("brj", "[4 10]")
        assertEquals(listOf(4, 10), value.`as`(listOfInt))
    }

    @Test
    fun `let test`() {
        val value = ctx.eval("brj", "(let [x 4, y 10] [x y])")
        assertEquals(listOf(4, 10), value.`as`(listOfInt))
    }

    @Test
    fun `exposes value in scope`() {
        ctx.eval("brj", "(def x 10)")
        ctx.eval("brj", """(def y "Hello")""")
        val bindings = ctx.getBindings("brj")
        assertEquals(setOf("x", "y"), bindings.memberKeys)
        assertEquals(10, bindings.getMember("x").asInt())
        assertEquals("Hello", bindings.getMember("y").asString())
        assertEquals(10, ctx.eval("brj", "x").asInt())
    }

    @Test
    fun `basic fn expr`() {
        ctx.eval("brj", "(def simple-vec (fn [x] [x]))")
        val bindings = ctx.getBindings("brj")
        assertEquals(
            listOf(10),
            bindings.getMember("simple-vec")
                .execute(10)
                .`as`(listOfInt)
        )
    }

    @Test
    fun `call expr`() {
        assertEquals(
            listOf(10, 5),
            ctx.eval("brj", "((fn [x y] [y x]) 5 10)")
                .`as`(listOfInt)
        )
    }

    @Test
    fun `test multiple exprs`() {
        assertEquals(10, ctx.eval("brj", "(def x 10) x").asInt())
    }

    @Test
    fun `test higher order fn`() {
        assertEquals(
            listOf(3),
            ctx.eval("brj", "((fn [f] (f 3)) (fn [x] [x]))")
                .`as`(listOfInt)
        )
    }

    @Test
    fun `rebound def`() {
        assertEquals(10, ctx.eval("brj", "(def x 10) x").asInt())
        assertEquals(10, ctx.eval("brj", "(def y (fn [] x)) (y)").asInt())
        assertEquals(15, ctx.eval("brj", "(def x 15) (y)").asInt())
    }
}
