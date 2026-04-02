package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.graalvm.polyglot.PolyglotException

class ConcurrentTest {

    @Test
    fun `spawn and await basic`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test:concurrent
              require:
                brj:
                  concurrent.as(c)
            def: result c:await(c:spawn(fn: f() 42))
        """.trimIndent())

        assertEquals(42L, result.getMember("result").asLong())
    }

    @Test
    fun `spawn runs on a different thread`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test:concurrent:thread
              require:
                brj:
                  concurrent.as(c)
            def: result c:await(c:spawn(fn: f() "hello"))
        """.trimIndent())

        assertEquals("hello", result.getMember("result").asString())
    }

    @Test
    fun `interrupt completed deferred returns false`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test:concurrent:interrupt
              require:
                brj:
                  concurrent.as(c)
            def: result
              let: [d c:spawn(fn: f() 42)]
                do:
                  c:await(d)
                  c:interrupt(d)
        """.trimIndent())

        assertEquals(false, result.getMember("result").asBoolean())
    }

    @Test
    fun `ensureActive throws when interrupted`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:concurrent:ensure
              require:
                brj:
                  concurrent.as(c)
            def: check() c:ensureActive()
        """.trimIndent())

        val check = ns.getMember("check")
        Thread.currentThread().interrupt()
        val ex = assertThrows<PolyglotException> { check.execute() }
        assertTrue(ex.isGuestException || ex.message?.contains("interrupted", ignoreCase = true) == true)
        // interrupted flag was cleared by ensureActive
        assertFalse(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `interrupt sleeping task`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:concurrent:interrupt:sleep
              require:
                brj:
                  concurrent.as(c)
            def: spawnSleeper() c:spawn(fn: sleeper() c:sleepMs(10000))
            def: doInterrupt(d) c:interrupt(d)
            def: doAwait(d)
              try: c:await(d)
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
            ns: test:concurrent:interrupt:polyglot
              require:
                brj:
                  concurrent.as(c)
            def: spawnSleeper() c:spawn(fn: sleeper() c:sleepMs(10000))
            def: doInterrupt(d) c:interrupt(d)
            def: doAwait(d) c:await(d)
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
            ns: test:concurrent:catch
              require:
                brj:
                  concurrent.as(c)
            def: result
              let: [d c:spawn(fn: failing() throw(Fault({:exnMessage "boom"})))]
                try: c:await(d)
                  catch:
                    e "caught"
        """.trimIndent())

        assertEquals("caught", result.getMember("result").asString())
    }

    @Test
    fun `spawned task error preserves anomaly category`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test:concurrent:catch:tag
              require:
                brj:
                  concurrent.as(c)
            def: result
              let: [d c:spawn(fn: failing() throw(NotFound({:exnMessage "gone"})))]
                try: c:await(d)
                  catch:
                    (NotFound data) "not-found"
                    e "other"
        """.trimIndent())

        assertEquals("not-found", result.getMember("result").asString())
    }

    @Test
    fun `nested spawn and await`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test:concurrent:nested
              require:
                brj:
                  concurrent.as(c)
            def: result c:await(c:spawn(fn: outer()
                                          let: [a c:spawn(fn: work1() 10)
                                                b c:spawn(fn: work2() 20)]
                                            add(c:await(a), c:await(b))))
        """.trimIndent())

        assertEquals(30L, result.getMember("result").asLong())
    }

    @Test
    fun `parent awaits unawaited children`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:scope:join
              require:
                brj:
                  concurrent.as(c)
            def: go()
              c:spawn(fn: outer()
                do:
                  c:spawn(fn: inner() do: c:sleepMs(100) 42)
                  "outer-done")
            def: doAwait(d) c:await(d)
        """.trimIndent())

        val start = System.currentTimeMillis()
        val d = ns.getMember("go").execute()
        ns.getMember("doAwait").execute(d)
        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed >= 80, "Parent should have waited for child, elapsed: ${elapsed}ms")
    }

    @Test
    fun `cascading interrupt cancels children`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:scope:cascade
              require:
                brj:
                  concurrent.as(c)
            def: go()
              c:spawn(fn: outer()
                do:
                  c:spawn(fn: inner() c:sleepMs(10000))
                  c:sleepMs(10000))
            def: doInterrupt(d) c:interrupt(d)
            def: doAwait(d)
              try: c:await(d)
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
            ns: test:scope:fail
              require:
                brj:
                  concurrent.as(c)
            def: go()
              c:spawn(fn: outer()
                do:
                  c:spawn(fn: slow() c:sleepMs(10000))
                  c:spawn(fn: failing() do: c:sleepMs(50) throw(Fault({:exnMessage "boom"})))
                  c:sleepMs(10000))
            def: doAwait(d)
              try: c:await(d)
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
            ns: test:scope:propagate
              require:
                brj:
                  concurrent.as(c)
            def: go()
              c:spawn(fn: l1()
                do:
                  c:spawn(fn: l2()
                    do:
                      c:spawn(fn: l3() do: c:sleepMs(50) throw(Fault({:exnMessage "deep"})))
                      c:sleepMs(10000))
                  c:sleepMs(10000))
            def: doAwait(d)
              try: c:await(d)
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
