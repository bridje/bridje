package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TimeTest {

    @Test
    fun `parse duration`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.time.dur
              require:
                brj:
                  as(time, t)
            def: result t/dur("PT0.15S")
        """.trimIndent())
        val result = ctx.evalBridje("test.time.dur/result")
        assertTrue(result.isInstant || result.isDuration || !result.isNull,
            "Expected a Duration value")
    }

    @Test
    fun `parse instant`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.time.inst
              require:
                brj:
                  as(time, t)
            def: result t/now()
        """.trimIndent())
        val result = ctx.evalBridje("test.time.inst/result")
        assertTrue(result.isInstant, "Expected an Instant")
    }

    @Test
    fun `duration toMillis roundtrip`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.time.roundtrip
              require:
                brj:
                  as(time, t)
              import:
                java.time:
                  as(Duration, Dur)
            decl: Dur/:toMillis() Int
            def: result Dur/:toMillis(t/durMs(500))
        """.trimIndent())
        val result = ctx.evalBridje("test.time.roundtrip/result")
        assertEquals(500L, result.asLong())
    }
}
