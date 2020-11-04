package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@TestInstance(PER_CLASS)
class BridjeLanguageTest {
    lateinit var ctx: Context

    @BeforeAll
    fun setup() {
        ctx = Context.newBuilder().allowAllAccess(true).build()
        ctx.enter()
    }

    @AfterAll
    fun tearDown() {
        ctx.leave()
        ctx.close()
    }

    @Test
    internal fun run() {
        val value = ctx.eval("brj", "[\"foo\"]")
        println(value)
    }
}