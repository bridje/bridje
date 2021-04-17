package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.TypeLiteral
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
class BridjeLanguageTest {
    lateinit var os: ByteArrayOutputStream
    lateinit var ctx: Context

    @BeforeEach
    fun setUp() {
        os = ByteArrayOutputStream()
        ctx = Context.newBuilder().allowAllAccess(true).out(os).build()
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
        assertTrue(bindings.memberKeys.containsAll(setOf("x", "y")))
        assertEquals(10, bindings.getMember("x").asInt())
        assertEquals("Hello", bindings.getMember("y").asString())
        assertEquals(10, ctx.eval("brj", "x").asInt())
    }

    @Test
    fun `basic fn expr`() {
        ctx.eval("brj", "(def (simple-vec x) [x])")
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
        assertEquals(10, ctx.eval("brj", "(def (y) x) (y)").asInt())
        assertEquals(15, ctx.eval("brj", "(def x 15) (y)").asInt())
    }

    @Test
    fun `test builtin`() {
        ctx.eval("brj", """(println0 "hello world!")""")
        assertEquals("hello world!\n", os.toString(StandardCharsets.UTF_8))
    }

    @Test
    fun `test top-level do`() {
        assertEquals(42, ctx.eval("brj", "(do (def x 42) x)").asInt())
    }

    @Test
    fun `test now-ms`() {
        val beforeMs = System.currentTimeMillis()
        val duringMs = ctx.eval("brj", "(now-ms0)").asLong()
        val afterMs = System.currentTimeMillis()

        assertTrue(duringMs in beforeMs..afterMs)
    }

    @Test
    fun `test defx`() {
        ctx.eval("brj", "(defx (println! Str) Str)")
        ctx.eval("brj", "(def println! println0)")
        ctx.eval("brj", """(println! "hello world!")""")

        assertEquals("hello world!\n", os.toString(StandardCharsets.UTF_8))
    }

    @Test
    fun `test with-fx`() {
        ctx.eval("brj", "(defx (now-ms!) Int)")
        ctx.eval("brj", "(def (now-ms!) (now-ms0))")

        assertEquals(0, ctx.eval("brj", "(with-fx [(def (now-ms!) 0)] (now-ms!))").asLong())

        assertEquals(0, ctx.eval("brj", "(with-fx [(def (now-ms!) 0)] (with-fx [] (now-ms!)))").asLong())

        val beforeMs = System.currentTimeMillis()
        val duringMs = ctx.eval("brj", "(with-fx [] (now-ms!))").asLong()
        val afterMs = System.currentTimeMillis()

        assertTrue(duringMs in beforeMs..afterMs)

        ctx.eval("brj", "(def (now-ms-caller) (now-ms!))")

        assertEquals(0, ctx.eval("brj", "(with-fx [(def (now-ms!) 0)] (now-ms-caller))").asLong())
    }

    @Test
    fun `test loop-recur`() {
        assertEquals((4 downTo 0).toList(), ctx.eval("brj", "(loop [x 5, res []] (if (zero? x) res (recur (dec x) (conjv0 res x))))").`as`(listOfInt))
    }

    @Test
    fun `test jclass`() {
        assertEquals(Instant.now(), ctx.eval("brj", """(jclass "java.time.Instant")""").invokeMember("now").asInstant())
    }

    @Test
    fun `test poly`() {
        assertEquals(0, ctx.eval("brj", """(poly "python" "import math; math")""").invokeMember("acos", 1).asInt())
    }

    @Test
    fun `test record`() {
        assertEquals(setOf("foo"), ctx.eval("brj", """{foo 42}""").memberKeys)
        assertEquals(42, ctx.eval("brj", """{foo 42}""").getMember("foo").asInt())
    }
}
