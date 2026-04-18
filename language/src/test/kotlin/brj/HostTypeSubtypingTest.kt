package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HostTypeSubtypingTest {

    @Test
    fun `ArrayList is subtype of Iterable`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.subtype.basic
              import:
                java.util:
                  as(ArrayList, AL)
                java.lang:
                  as(Iterable, Itr)
            decl: [a] AL/new() AL(a)
            decl: [a] useItr(Itr(a)) Int
            def: useItr(x) 1
            def: result useItr(AL/new())
        """.trimIndent())
        val result = ctx.evalBridje("test.subtype.basic/result")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `type arg propagation through hierarchy`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.subtype.propagation
              import:
                java.util:
                  as(ArrayList, AL)
                  as(Iterator, Iter)
                java.lang:
                  as(Iterable, Itr)
            decl: [a] AL/new() AL(a)
            decl: [a] AL/:add(a) Bool
            decl: [a] Itr/:iterator() Iter(a)
            decl: [a] Iter/:next() a
            def: firstViaIterable(itr) Iter/:next(Itr/:iterator(itr))
            def: result
              let: [xs AL/new()]
                do:
                  AL/:add(xs, 42)
                  firstViaIterable(xs)
        """.trimIndent())
        val result = ctx.evalBridje("test.subtype.propagation/result")
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `unrelated classes are rejected`() = withContext { ctx ->
        val ex = assertThrows<PolyglotException> {
            ctx.evalBridje("""
                ns: test.subtype.reject
                  import:
                    java.lang:
                      as(StringBuilder, SB)
                      as(Iterable, Itr)
                decl: SB/new() SB
                decl: [a] Itr/:iterator() Itr(a)
                def: result Itr/:iterator(SB/new())
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("not a subtype") == true || ex.message?.contains("Incompatible") == true,
            "Expected subtype error, got: ${ex.message}")
    }

    @Test
    fun `erased HostTypes with hierarchy`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.subtype.erased
              import:
                java.util:
                  as(ArrayList, AL)
                java.lang:
                  as(Iterable, Itr)
            decl: AL/new() AL
            decl: useItr(Itr) Int
            def: useItr(x) 1
            def: result useItr(AL/new())
        """.trimIndent())
        val result = ctx.evalBridje("test.subtype.erased/result")
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `transitive subtyping through hierarchy`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.subtype.transitive
              import:
                java.util:
                  as(LinkedList, LL)
                java.lang:
                  as(Iterable, Itr)
            decl: [a] LL/new() LL(a)
            decl: [a] useItr(Itr(a)) Int
            def: useItr(x) 1
            def: result useItr(LL/new())
        """.trimIndent())
        val result = ctx.evalBridje("test.subtype.transitive/result")
        assertEquals(1L, result.asLong())
    }
}
