package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VectorTest {
    private lateinit var ctx: Context

    @BeforeEach
    fun setUp() {
        ctx = Context.newBuilder()
            .logHandler(System.err)
            .build()
        ctx.enter()
    }

    @AfterEach
    fun tearDown() {
        ctx.leave()
        ctx.close()
    }

    private fun eval(code: String): Value = ctx.eval("bridje", code)

    @Test
    fun `empty vector`() {
        val result = eval("[]")
        assertTrue(result.hasArrayElements())
        assertEquals(0, result.arraySize)
    }

    @Test
    fun `vector of integers`() {
        val result = eval("[1 2 3]")
        assertTrue(result.hasArrayElements())
        assertEquals(3, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals(2L, result.getArrayElement(1).asLong())
        assertEquals(3L, result.getArrayElement(2).asLong())
    }

    @Test
    fun `nested vectors`() {
        val result = eval("[[1 2] [3 4]]")
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
    fun `mixed type vector`() {
        val result = eval("[1 \"hello\" 3.14]")
        assertTrue(result.hasArrayElements())
        assertEquals(3, result.arraySize)
        assertEquals(1L, result.getArrayElement(0).asLong())
        assertEquals("hello", result.getArrayElement(1).asString())
        assertEquals(3.14, result.getArrayElement(2).asDouble())
    }

    @Test
    fun `vector display string`() {
        val result = eval("[1 2 3]")
        assertEquals("[1 2 3]", result.toString())
    }

    // Interop tests - accessing BridjeVector through Graal polyglot API

    @Test
    fun `interop - convert to Java List`() {
        val result = eval("[1 2 3]")
        val list = result.`as`(List::class.java)
        assertEquals(3, list.size)
        assertEquals(1L, list[0])
        assertEquals(2L, list[1])
        assertEquals(3L, list[2])
    }

    @Test
    fun `interop - iterate with iterator`() {
        val result = eval("[10 20 30]")
        assertTrue(result.hasIterator())

        val values = mutableListOf<Long>()
        val iterator = result.iterator
        while (iterator.hasIteratorNextElement()) {
            values.add(iterator.iteratorNextElement.asLong())
        }
        assertEquals(listOf(10L, 20L, 30L), values)
    }

    @Test
    fun `interop - out of bounds throws`() {
        val result = eval("[1 2]")
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            result.getArrayElement(5)
        }
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            result.getArrayElement(-1)
        }
    }

    @Test
    fun `interop - array size check`() {
        val result = eval("[1 2 3]")
        assertEquals(3L, result.arraySize)
        // valid indices are 0, 1, 2 - accessing 3 or -1 throws (tested in out of bounds test)
    }
}
