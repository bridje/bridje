package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LoopRecurTest {
    @Test
    fun `basic loop - sum 0 to 9`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (loop [i 0 acc 0]
              (if (brj.core/lt i 10)
                (recur (brj.core/add i 1) (brj.core/add acc i))
                acc))
        """.trimIndent())
        assertEquals(45L, result.asLong())
    }

    @Test
    fun `loop with single binding`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (loop [i 10]
              (if (brj.core/lt i 1)
                i
                (recur (brj.core/sub i 1))))
        """.trimIndent())
        assertEquals(0L, result.asLong())
    }

    @Test
    fun `loop with no recur - returns immediately`() = withContext { ctx ->
        val result = ctx.evalBridje("(loop [x 42] x)")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `recur in do - tail position`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (loop [i 0 acc 0]
              (if (brj.core/lt i 5)
                (do
                  (brj.core/add 1 1)
                  (recur (brj.core/add i 1) (brj.core/add acc i)))
                acc))
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `recur outside loop or fn - compile error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("(recur 1 2)")
        }
        assertTrue(ex.message?.contains("recur outside of loop or fn") == true, "Expected recur-outside-loop-or-fn error, got: ${ex.message}")
    }

    @Test
    fun `recur arity mismatch - compile error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                (loop [i 0 acc 0]
                  (recur 1))
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("recur expects 2 arguments, got 1") == true, "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `nested if with recur in both branches`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (loop [i 0 acc 0]
              (if (brj.core/lt i 10)
                (if (brj.core/lt i 5)
                  (recur (brj.core/add i 1) (brj.core/add acc i))
                  (recur (brj.core/add i 1) acc))
                acc))
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }

    @Test
    fun `loop in let body`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (let [n 10]
              (loop [i 0 acc 0]
                (if (brj.core/lt i n)
                  (recur (brj.core/add i 1) (brj.core/add acc i))
                  acc)))
        """.trimIndent())
        assertEquals(45L, result.asLong())
    }

    @Test
    fun `recur in let binding - not tail position`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                (loop [x 0]
                  (let [y (recur 1)] y))
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("recur outside of loop or fn") == true, "Expected recur-outside error, got: ${ex.message}")
    }

    @Test
    fun `fn-targeting recur - countdown`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (let [countdown (fn (countdown n)
                              (if (brj.core/lt n 1)
                                0
                                (recur (brj.core/sub n 1))))]
              (countdown 10))
        """.trimIndent())
        assertEquals(0L, result.asLong())
    }

    @Test
    fun `fn-targeting recur - factorial`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (let [factorial (fn (factorial n acc)
                              (if (brj.core/lt n 2)
                                acc
                                (recur (brj.core/sub n 1) (brj.core/mul n acc))))]
              (factorial 10 1))
        """.trimIndent())
        assertEquals(3628800L, result.asLong())
    }

    @Test
    fun `fn-targeting recur arity mismatch`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                (fn (foo a b) (recur 1))
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("recur expects 2 arguments, got 1") == true, "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `loop inside fn shadows fn recur target`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            (let [f (fn (f n)
                      (loop [i 0 acc 0]
                        (if (brj.core/lt i n)
                          (recur (brj.core/add i 1) (brj.core/add acc i))
                          acc)))]
              (f 5))
        """.trimIndent())
        assertEquals(10L, result.asLong())
    }
}
