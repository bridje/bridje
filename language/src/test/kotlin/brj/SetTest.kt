package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SetTest {
    @Test
    fun `empty set from empty vector`() = withContext { ctx ->
        val result = ctx.evalBridje("set([])")
        assertTrue(result.hasIterator())
        assertFalse(result.iterator.hasIteratorNextElement())
    }

    @Test
    fun `set from vector has iterator`() = withContext { ctx ->
        val result = ctx.evalBridje("set([1, 2, 3])")
        assertTrue(result.hasIterator())
    }

    @Test
    fun `set count`() = withContext { ctx ->
        assertEquals(3L, ctx.evalBridje("set-count(set([1, 2, 3]))").asLong())
        assertEquals(0L, ctx.evalBridje("set-count(set([]))").asLong())
    }

    @Test
    fun `set deduplicates`() = withContext { ctx ->
        assertEquals(2L, ctx.evalBridje("set-count(set([1, 2, 1, 2, 1]))").asLong())
    }

    @Test
    fun `set-empty on empty set is true`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("set-empty?(set([]))").asBoolean())
    }

    @Test
    fun `set-empty on non-empty set is false`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("set-empty?(set([1]))").asBoolean())
    }

    @Test
    fun `contains on member`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("contains?(set([1, 2, 3]), 2)").asBoolean())
    }

    @Test
    fun `contains on non-member`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("contains?(set([1, 2, 3]), 42)").asBoolean())
    }

    @Test
    fun `conj adds element`() = withContext { ctx ->
        val result = ctx.evalBridje("set-count(conj(4, set([1, 2, 3])))")
        assertEquals(4L, result.asLong())
    }

    @Test
    fun `conj existing element is no-op`() = withContext { ctx ->
        val result = ctx.evalBridje("set-count(conj(2, set([1, 2, 3])))")
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `conj result contains new element`() = withContext { ctx ->
        assertTrue(ctx.evalBridje("contains?(conj(42, set([1, 2, 3])), 42)").asBoolean())
    }

    @Test
    fun `disj removes element`() = withContext { ctx ->
        val result = ctx.evalBridje("set-count(disj(2, set([1, 2, 3])))")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `disj missing element is no-op`() = withContext { ctx ->
        val result = ctx.evalBridje("set-count(disj(42, set([1, 2, 3])))")
        assertEquals(3L, result.asLong())
    }

    @Test
    fun `disj result does not contain removed element`() = withContext { ctx ->
        assertFalse(ctx.evalBridje("contains?(disj(2, set([1, 2, 3])), 2)").asBoolean())
    }

    @Test
    fun `union combines sets`() = withContext { ctx ->
        val result = ctx.evalBridje("set-count(union(set([1, 2]), set([2, 3, 4])))")
        assertEquals(4L, result.asLong())
    }

    @Test
    fun `union with empty`() = withContext { ctx ->
        assertEquals(3L, ctx.evalBridje("set-count(union(set([1, 2, 3]), set([])))").asLong())
        assertEquals(3L, ctx.evalBridje("set-count(union(set([]), set([1, 2, 3])))").asLong())
    }

    @Test
    fun `set is immutable - conj returns new set`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            let: [s set([1, 2, 3])
                  t conj(4, s)]
              sub(set-count(t), set-count(s))
        """.trimIndent())
        assertEquals(1L, result.asLong())
    }

    @Test
    fun `set iteration yields all elements`() = withContext { ctx ->
        val result = ctx.evalBridje("set([10, 20, 30])")
        assertTrue(result.hasIterator())
        val iter = result.iterator
        val seen = mutableSetOf<Long>()
        while (iter.hasIteratorNextElement()) {
            seen.add(iter.iteratorNextElement.asLong())
        }
        assertEquals(setOf(10L, 20L, 30L), seen)
    }

    @Test
    fun `set display string`() = withContext { ctx ->
        val result = ctx.evalBridje("set([1, 2, 3])")
        assertEquals("#{1 2 3}", result.toString())
    }

    @Test
    fun `empty set display string`() = withContext { ctx ->
        val result = ctx.evalBridje("set([])")
        assertEquals("#{}", result.toString())
    }

    @Test
    fun `set flows into reduce via Iterable subtyping`() = withContext { ctx ->
        // Sums the elements of a set by passing it where an Iterable is expected.
        // Proves SetType <: IterableType at the type level.
        val result = ctx.evalBridje("reduce(set([1, 2, 3]), 0, add)")
        assertEquals(6L, result.asLong())
    }

    @Test
    fun `set flows into mapv via Iterable subtyping`() = withContext { ctx ->
        val result = ctx.evalBridje("count(mapv(set([1, 2, 3]), #: add(it, 1)))")
        assertEquals(3L, result.asLong())
    }
}
