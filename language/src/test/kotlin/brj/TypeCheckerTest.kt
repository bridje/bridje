package brj

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TypeCheckerTest {

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

    private fun eval(src: String) = ctx.eval("brj", src)

    @Test
    internal fun `test simple typings`() {
        assertEquals("(Typing {}, [] => Int)", eval("(type 42)").toString())
        assertEquals("(Typing {}, [] => Str)", eval("""(type "Hello world!")""").toString())
    }

    @Test
    internal fun `test coll typing`() {
        assertEquals("(Typing {}, [] => [Int])", eval("(type [1 2 3])").toString())
        assertEquals("(Typing {}, [] => #{Int})", eval("(type #{1 2 3})").toString())
    }

    @Test
    internal fun `test record typing`() {
        assertEquals("(Typing {}, [] => {:a Int, :b Bool})", eval("(type {:a 1, :b false})").toString())
        assertEquals("(Typing {}, [] => Int)", eval("(type (:a {:a 1, :b false}))").toString())
    }
}