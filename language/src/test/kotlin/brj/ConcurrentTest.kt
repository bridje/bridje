package brj

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.graalvm.polyglot.PolyglotException

class ConcurrentTest {

    @Test
    fun `spawn and await basic`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.concurrent
              require:
                brj:
                  as(concurrent, c)
            def: result c/await(c/spawn(fn: f() 42))
        """.trimIndent())

        assertEquals(42L, result.getMember("result").asLong())
    }

    @Test
    fun `spawn runs on a different thread`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.concurrent.thread
              require:
                brj:
                  as(concurrent, c)
            def: result c/await(c/spawn(fn: f() "hello"))
        """.trimIndent())

        assertEquals("hello", result.getMember("result").asString())
    }

    @Test
    fun `interrupt completed deferred returns false`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.concurrent.interrupt
              require:
                brj:
                  as(concurrent, c)
            def: result
              let: [d c/spawn(fn: f() 42)]
                do:
                  c/await(d)
                  c/interrupt(d)
        """.trimIndent())

        assertEquals(false, result.getMember("result").asBoolean())
    }

    @Test
    fun `ensure-active throws when interrupted`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.concurrent.ensure
              require:
                brj:
                  as(concurrent, c)
            def: check() c/ensure-active()
        """.trimIndent())

        val check = ns.getMember("check")
        Thread.currentThread().interrupt()
        val ex = assertThrows<PolyglotException> { check.execute() }
        assertTrue(ex.isGuestException || ex.message?.contains("interrupted", ignoreCase = true) == true)
        // interrupted flag was cleared by ensure-active
        assertFalse(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `interrupt sleeping task`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.concurrent.interrupt.sleep
              require:
                brj:
                  as(concurrent, c)
            def: spawnSleeper() c/spawn(fn: sleeper() c/sleep-ms(10000))
            def: doInterrupt(d) c/interrupt(d)
            def: doAwait(d)
              try: c/await(d)
                catch:
                  (Interrupted data) "interrupted"
                  e "other"
        """.trimIndent())

        val d = ns.getMember("spawnSleeper").execute()
        Thread.sleep(50)
        ns.getMember("doInterrupt").execute(d)
        assertEquals("interrupted", ns.getMember("doAwait").execute(d).asString())
        Thread.sleep(50) // let interrupted thread wind down before context closes
    }

    @Test
    fun `interrupt surfaces as interrupted polyglot exception`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.concurrent.interrupt.polyglot
              require:
                brj:
                  as(concurrent, c)
            def: spawnSleeper() c/spawn(fn: sleeper() c/sleep-ms(10000))
            def: doInterrupt(d) c/interrupt(d)
            def: doAwait(d) c/await(d)
        """.trimIndent())

        val d = ns.getMember("spawnSleeper").execute()
        Thread.sleep(50)
        ns.getMember("doInterrupt").execute(d)
        val ex = assertThrows<PolyglotException> { ns.getMember("doAwait").execute(d) }
        assertTrue(ex.isGuestException, "Expected guest exception")
        assertTrue(ex.isInterrupted, "Expected interrupted exception")
    }

    @Test
    fun `spawned task error is catchable`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.concurrent.catch
              require:
                brj:
                  as(concurrent, c)
            def: result
              let: [d c/spawn(fn: failing() throw(Fault({:exnMessage "boom"})))]
                try: c/await(d)
                  catch:
                    e "caught"
        """.trimIndent())

        assertEquals("caught", result.getMember("result").asString())
    }

    @Test
    fun `spawned task error preserves anomaly category`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.concurrent.catch.tag
              require:
                brj:
                  as(concurrent, c)
            def: result
              let: [d c/spawn(fn: failing() throw(NotFound({:exnMessage "gone"})))]
                try: c/await(d)
                  catch:
                    (NotFound data) "not-found"
                    e "other"
        """.trimIndent())

        assertEquals("not-found", result.getMember("result").asString())
    }

    @Test
    fun `nested spawn and await`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.concurrent.nested
              require:
                brj:
                  as(concurrent, c)
            def: result c/await(c/spawn(fn: outer()
                                          let: [a c/spawn(fn: work1() 10)
                                                b c/spawn(fn: work2() 20)]
                                            add(c/await(a), c/await(b))))
        """.trimIndent())

        assertEquals(30L, result.getMember("result").asLong())
    }

    @Test
    @Disabled("Fails reliably on GitHub Actions CI, passes reliably locally. Tracked by #114.")
    fun `parent awaits unawaited children`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.scope.join
              require:
                brj:
                  as(concurrent, c)
              import:
                java.util.concurrent.atomic:
                  as(AtomicBoolean, AB)
            decl: AB/.set(Bool) Nothing
            def: go(flag)
              c/spawn(fn: outer()
                c/spawn(fn: inner()
                  c/sleep-ms(50)
                  AB/.set(flag, true)
                  nil)
                "outer-done")
            def: doAwait(d) c/await(d)
        """.trimIndent())

        val flag = AtomicBoolean(false)
        val d = ns.getMember("go").execute(flag)
        val result = ns.getMember("doAwait").execute(d)
        assertEquals("outer-done", result.asString())
        assertTrue(flag.get(), "Parent returned before unawaited child finished")
    }

    @Test
    fun `cascading interrupt cancels children`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.scope.cascade
              require:
                brj:
                  as(concurrent, c)
            def: go()
              c/spawn(fn: outer()
                do:
                  c/spawn(fn: inner() c/sleep-ms(10000))
                  c/sleep-ms(10000))
            def: doInterrupt(d) c/interrupt(d)
            def: doAwait(d)
              try: c/await(d)
                catch:
                  (Interrupted data) "interrupted"
                  e "other"
        """.trimIndent())

        val d = ns.getMember("go").execute()
        Thread.sleep(50)
        ns.getMember("doInterrupt").execute(d)
        val start = System.currentTimeMillis()
        val result = ns.getMember("doAwait").execute(d)
        val elapsed = System.currentTimeMillis() - start
        assertEquals("interrupted", result.asString())
        assertTrue(elapsed < 2000, "Should resolve quickly after interrupt, elapsed: ${elapsed}ms")
        Thread.sleep(50) // let cancelled threads wind down before context closes
    }

    @Test
    fun `failed child cancels siblings`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.scope.fail
              require:
                brj:
                  as(concurrent, c)
            def: go()
              c/spawn(fn: outer()
                do:
                  c/spawn(fn: slow() c/sleep-ms(10000))
                  c/spawn(fn: failing() do: c/sleep-ms(50) throw(Fault({:exnMessage "boom"})))
                  c/sleep-ms(10000))
            def: doAwait(d)
              try: c/await(d)
                catch:
                  (Fault data) "fault"
                  e "other"
        """.trimIndent())

        val d = ns.getMember("go").execute()
        val start = System.currentTimeMillis()
        val result = ns.getMember("doAwait").execute(d)
        val elapsed = System.currentTimeMillis() - start
        assertEquals("fault", result.asString())
        assertTrue(elapsed < 2000, "Should resolve quickly after child failure, elapsed: ${elapsed}ms")
        Thread.sleep(50) // let cancelled threads wind down before context closes
    }

    @Test
    fun `error propagates up the tree`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.scope.propagate
              require:
                brj:
                  as(concurrent, c)
            def: go()
              c/spawn(fn: l1()
                do:
                  c/spawn(fn: l2()
                    do:
                      c/spawn(fn: l3() do: c/sleep-ms(50) throw(Fault({:exnMessage "deep"})))
                      c/sleep-ms(10000))
                  c/sleep-ms(10000))
            def: doAwait(d)
              try: c/await(d)
                catch:
                  (Fault data) "fault"
                  e "other"
        """.trimIndent())

        val d = ns.getMember("go").execute()
        val start = System.currentTimeMillis()
        val result = ns.getMember("doAwait").execute(d)
        val elapsed = System.currentTimeMillis() - start
        assertEquals("fault", result.asString())
        assertTrue(elapsed < 2000, "Should resolve quickly after deep failure, elapsed: ${elapsed}ms")
        Thread.sleep(50) // let cancelled threads wind down before context closes
    }
}
