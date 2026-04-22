package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EffectTest {

    @Test
    fun `defx declares effect var with default`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.fx1
            (defx myId (Fn [Int] Int) (fn (id x) x))
        """.trimIndent())
        val myId = ns.getMember("myId")
        assertNotNull(myId, "myId should be accessible as a member")
        assertTrue(myId.canExecute(), "myId default should be executable")
        assertEquals(42L, myId.execute(42L).asLong())
    }

    @Test
    fun `defx accepts call-shape signature`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.fx.callshape
            defx: myId(Int) Int
              fn: default(x) x
        """.trimIndent())
        val myId = ns.getMember("myId")
        assertNotNull(myId)
        assertTrue(myId.canExecute())
        assertEquals(42L, myId.execute(42L).asLong())
    }

    @Test
    fun `defx call-shape signature with withFx override`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              defx: bump(Int) Int
                fn: default(x) x
              def: apply(n) bump(n)
              withFx: [bump #: add(it, 1)]
                apply(5)
        """.trimIndent())
        assertEquals(6L, result.asLong())
    }

    @Test
    fun `defx call-shape signature without default`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.fx.nodefault
            defx: noDefault(Int) Int
            def: usesIt(n) noDefault(n)
        """.trimIndent())
        assertNotNull(ns.getMember("usesIt"))
    }

    @Test
    fun `effect var callable at top level`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx myId (Fn [Int] Int) (fn (id x) x))
              (myId 42))
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `defx with default, used in fn`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx transform (Fn [Int] Int) (fn (id x) x))
              (def (apply n) (transform n))
              (apply 42))
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `withFx overrides effect`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx transform (Fn [Int] Int) (fn (id x) x))
              (def (apply n) (transform n))
              (withFx [transform (fn (inc x) (add x 1))]
                (apply 10)))
        """.trimIndent())
        assertEquals(11L, result.asLong())
    }

    @Test
    fun `transitive effects`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx base (Fn [Int] Int) (fn (id x) x))
              (def (inner x) (base x))
              (def (outer x) (inner x))
              (withFx [base (fn (add100 x) (add x 100))]
                (outer 5)))
        """.trimIndent())
        assertEquals(105L, result.asLong())
    }

    @Test
    fun `multiple effects`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx e1 (Fn [Int] Int) (fn (id x) x))
              (defx e2 (Fn [Int] Int) (fn (id x) x))
              (def (both x) (add (e1 x) (e2 x)))
              (withFx [e1 (fn (add10 x) (add x 10))
                       e2 (fn (add100 x) (add x 100))]
                (both 1)))
        """.trimIndent())
        assertEquals(112L, result.asLong())
    }

    @Test
    fun `nested withFx`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx e1 (Fn [Int] Int) (fn (id x) x))
              (defx e2 (Fn [Int] Int) (fn (id x) x))
              (def (both x) (add (e1 x) (e2 x)))
              (withFx [e1 (fn (add10 x) (add x 10))]
                (withFx [e2 (fn (add100 x) (add x 100))]
                  (both 1))))
        """.trimIndent())
        assertEquals(112L, result.asLong())
    }

    @Test
    fun `pure fn calling effectful fn`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx base (Fn [Int] Int) (fn (id x) x))
              (def (effectful x) (base x))
              (def (pure x) (add x 1))
              (def (mixed x) (pure (effectful x)))
              (withFx [base (fn (dbl x) (add x x))]
                (mixed 5)))
        """.trimIndent())
        assertEquals(11L, result.asLong())
    }

    @Test
    fun `defx in value position is error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                (do
                  (let [x (defx e (Fn [Int] Int))]
                    x))
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("defx not allowed in value position") == true,
            "Expected 'defx not allowed in value position', got: ${ex.message}")
    }

    @Test
    fun `withFx unknown effect is error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                (do
                  (withFx [nonexistent 42] 1))
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Unknown effect") == true,
            "Expected 'Unknown effect', got: ${ex.message}")
    }

    @Test
    fun `effect in namespace`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.fx.ns
            (defx myEffect (Fn [Int] Int) (fn (id x) x))
            (def (useEffect n) (myEffect n))
        """.trimIndent())
        val myEffect = ns.getMember("myEffect")
        assertNotNull(myEffect, "myEffect should be accessible as a member")
        assertTrue(myEffect.canExecute(), "myEffect default should be executable")
        assertEquals(42L, myEffect.execute(42L).asLong())
        val useEffect = ns.getMember("useEffect")
        assertNotNull(useEffect, "useEffect should be accessible")
        assertTrue(useEffect.canExecute(), "useEffect should be executable (outer stage)")
    }

    @Test
    fun `repeated calls to effectful fn`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx transform (Fn [Int] Int) (fn (id x) x))
              (def (apply n) (transform n))
              (def (repeated)
                (add (add (apply 1) (apply 2)) (apply 3)))
              (withFx [transform (fn (dbl x) (add x x))]
                (repeated)))
        """.trimIndent())
        assertEquals(12L, result.asLong())
    }

    @Test
    fun `inner lambda calling effectful fn`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx transform (Fn [Int] Int) (fn (id x) x))
              (def (apply n) (transform n))
              (def (outer n)
                (let [f (fn (inner x) (apply x))]
                  (f n)))
              (withFx [transform (fn (add10 x) (add x 10))]
                (outer 5)))
        """.trimIndent())
        assertEquals(15L, result.asLong())
    }

    @Test
    fun `withFx with multiple effectful calls in body`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (do
              (defx e1 (Fn [Int] Int) (fn (id x) x))
              (def (use-e1 x) (e1 x))
              (withFx [e1 (fn (mul2 x) (add x x))]
                (add (use-e1 3) (use-e1 7))))
        """.trimIndent())
        assertEquals(20L, result.asLong())
    }

    @Test
    fun `effect var across namespaces`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.fx.lib
            (defx bump (Fn [Int] Int) (fn (default n) n))
            (def (apply n) (bump n))
        """.trimIndent())

        val ns = ctx.evalBridje("""
            ns: test.fx.consumer
              require:
                test.fx: lib
            (def (use n)
              (withFx [lib/bump (fn (plus10 x) (add x 10))]
                (lib/apply n)))
        """.trimIndent())

        assertEquals(15L, ns.getMember("use").execute(5L).asLong())
    }
}
