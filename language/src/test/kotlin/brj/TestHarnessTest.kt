package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TestHarnessTest {

    @Test
    fun `is passing assertion records no failures`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.harness.pass
              require:
                brj: as(test, t)

            def: myTest() t/is(eq(1, 1))

            def: runIt() t/run-test(myTest)
        """.trimIndent())

        val result = ns.getMember("runIt").execute()
        assertEquals(0L, result.getMember("failures").arraySize)
        assertTrue(result.getMember("exn").isNull)
    }

    @Test
    fun `is failing assertion records a failure`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.harness.fail
              require:
                brj: as(test, t)

            def: myTest() t/is(eq(1, 2))

            def: runIt() t/run-test(myTest)
        """.trimIndent())

        val result = ns.getMember("runIt").execute()
        assertEquals(1L, result.getMember("failures").arraySize)
        assertTrue(result.getMember("exn").isNull)
    }

    @Test
    fun `multiple is calls in one test collect multiple failures`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.harness.multi
              require:
                brj: as(test, t)

            def: myTest()
              t/is(eq(1, 1))
              t/is(eq(1, 2))
              t/is(eq(2, 3))

            def: runIt() t/run-test(myTest)
        """.trimIndent())

        val result = ns.getMember("runIt").execute()
        assertEquals(2L, result.getMember("failures").arraySize)
    }

    @Test
    fun `exception in test body is captured in exn`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.harness.exn
              require:
                brj: as(test, t)

            def: myTest() throw(Fault({:exnMessage "boom"}))

            def: runIt() t/run-test(myTest)
        """.trimIndent())

        val result = ns.getMember("runIt").execute()
        assertFalse(result.getMember("exn").isNull)
    }

    @Test
    fun `failures recorded before throw are preserved alongside exn`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.harness.failThenThrow
              require:
                brj: as(test, t)

            def: myTest()
              t/is(eq(1, 2))
              throw(Fault({:exnMessage "boom"}))

            def: runIt() t/run-test(myTest)
        """.trimIndent())

        val result = ns.getMember("runIt").execute()
        assertEquals(1L, result.getMember("failures").arraySize)
        assertFalse(result.getMember("exn").isNull)
    }

    @Test
    fun `failure record exposes form and message members`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.harness.members
              require:
                brj: as(test, t)

            def: myTest() t/is(eq(1, 2))

            def: runIt() t/run-test(myTest)
        """.trimIndent())

        val result = ns.getMember("runIt").execute()
        val failures = result.getMember("failures")
        assertEquals(1L, failures.arraySize)

        val failure = failures.getArrayElement(0)
        assertTrue(failure.hasMember("form"))
        assertTrue(failure.hasMember("message"))
    }
}
