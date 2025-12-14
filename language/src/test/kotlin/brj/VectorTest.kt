package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VectorTest {
    @Test
    fun `empty vector`() = withContext { ctx ->
        val result = ctx.evalBridje("[]")
        assertTrue(result.hasArrayElements())
        assertEquals(0, result.arraySize)
    }

    @Test
    fun `vector of integers`() = withContext { ctx ->
        val result = ctx.evalBridje("[1 2 3]")
        assertTrue(result.hasArrayElements())
        assertEquals(3, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(2L, result.getArrayElement(1).asLong())
        assertEquals(3L, result.getArrayElement(2).asLong())
    }

    @Test
    fun `nested vectors`() = withContext { ctx ->
        val result = ctx.evalBridje("[[1 2] [3 4]]")
        assertTrue(result.hasArrayElements())
        assertEquals(2, result.arraySize)

        val first = result.getArrayElement(0)
        assertTrue(first.hasArrayElements())
        assertEquals(2, first.arraySize)
        assertEquals(1L, first.getArrayElement(0).asLong())
        assertEquals(2L, first.getArrayElement(1).asLong())

        val second = result.getArrayElement(1)
        assertTrue(second.hasArrayElements())
        assertEquals(3L, second.getArrayElement(0).asLong())
        assertEquals(4L, second.getArrayElement(1).asLong())
    }

    @Test
    fun `mixed type vector`() = withContext { ctx ->
        val result = ctx.evalBridje("[1 \"hello\" 3.14]")
        assertTrue(result.hasArrayElements())
        assertEquals(3, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals("hello", result.getArrayElement(1).asString())
        assertEquals(3.14, result.getArrayElement(2).asDouble())
    }

    @Test
    fun `vector display string`() = withContext { ctx ->
        val result = ctx.evalBridje("[1 2 3]")
        assertEquals("[1 2 3]", result.toString())
    }

    // Interop tests - accessing BridjeVector through Graal polyglot API

    @Test
    fun `interop - convert to Java List`() = withContext { ctx ->
        val result = ctx.evalBridje("[1 2 3]")
        val list = result.`as`(List::class.java)
        assertEquals(3, list.size)
        assertEquals(1L, list[0])
        assertEquals(2L, list[1])
        assertEquals(3L, list[2])
    }

    @Test
    fun `interop - iterate with iterator`() = withContext { ctx ->
        val result = ctx.evalBridje("[10 20 30]")
        assertTrue(result.hasIterator())

        val values = mutableListOf<Long>()
        val iterator = result.iterator
        while (iterator.hasIteratorNextElement()) {
            values.add(iterator.iteratorNextElement.asLong())
        }
        assertEquals(listOf(10L, 20L, 30L), values)
    }

    @Test
    fun `interop - out of bounds throws`() = withContext { ctx ->
        val result = ctx.evalBridje("[1 2]")
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            result.getArrayElement(5)
        }
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            result.getArrayElement(-1)
        }
    }

    @Test
    fun `interop - array size check`() = withContext { ctx ->
        val result = ctx.evalBridje("[1 2 3]")
        assertEquals(3L, result.arraySize)
    }
}
