package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IteratorTest {

    @Test
    fun `itr on vector returns iterator`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            let: [it itr([1, 2, 3])]
              itrHasNext?(it)
        """.trimIndent())
        assertTrue(result.asBoolean())
    }

    @Test
    fun `itrNext returns elements in order`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            def: result
              let: [it itr([10, 20, 30])]
                loop: [acc 0]
                  if: itrHasNext?(it)
                    recur: add(acc, itrNext(it))
                    acc
        """.trimIndent())
        assertEquals(60L, result.asLong())
    }

    @Test
    fun `itr works on Java ArrayList`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.iter.java
              import:
                java.util:
                  as(ArrayList, AL)
            decl: AL/new() AL
            decl: [a] :AL/add(a) Bool
            def: result
              let: [xs AL/new()]
                do:
                  :AL/add(xs, 10)
                  :AL/add(xs, 20)
                  let: [it itr(xs)]
                    loop: [acc 0]
                      if: itrHasNext?(it)
                        recur: add(acc, itrNext(it))
                        acc
        """.trimIndent())
        val result = ctx.evalBridje("test.iter.java/result")
        assertEquals(30L, result.asLong())
    }

    @Test
    fun `vector satisfies Iterable type parameter`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.iter.type.vec
            decl: [a] useIterable(Iterable(a)) Int
            def: useIterable(x) 1
            def: result useIterable([1, 2, 3])
        """.trimIndent())
        val result = ctx.evalBridje("test.iter.type.vec/result")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `java Iterable satisfies Iterable type parameter`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.iter.type.jiter
              import:
                java.util:
                  as(ArrayList, AL)
            decl: AL/new() AL
            decl: [a] useIterable(Iterable(a)) Int
            def: useIterable(x) 1
            def: result useIterable(AL/new())
        """.trimIndent())
        val result = ctx.evalBridje("test.iter.type.jiter/result")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `element type propagates through itr`() = withContext { ctx ->
        // itr([Int]) should give Iterator(Int), itrNext should return Int
        // add requires matching types, so this verifies propagation
        val result = ctx.evalBridje("""
            def: result
              let: [it itr([10, 20])]
                add(itrNext(it), itrNext(it))
        """.trimIndent())
        assertEquals(30L, result.asLong())
    }

    @Test
    fun `non-iterable type is rejected`() = withContext { ctx ->
        val ex = assertThrows<PolyglotException> {
            ctx.evalBridje("""
                ns: test.iter.type.reject
                def: result itr(42)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("not Iterable") == true || ex.message?.contains("Incompatible") == true,
            "Expected type error for non-iterable, got: ${ex.message}")
    }
}
