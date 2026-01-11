package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NsClasspathLoadingTest {
    @Test
    fun `loads namespace from classpath`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: consumer
              require:
                testlib:
                  base
            testlib:base/value
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `loads namespace with alias`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: consumer
              require:
                testlib:
                  base.as(b)
            b/value
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `loads transitive dependencies`() = withContext { ctx ->
        // chain_top requires chain_middle and base
        // chain_middle requires base
        val result = ctx.evalBridje("""
            ns: consumer
              require:
                testlib:
                  chain_top
            testlib:chain_top/total
        """.trimIndent())
        // base.value = 42, chain_middle.doubled = 84, chain_top.total = 84 + 42 = 126
        assertEquals(126L, result.asLong())
    }

    @Test
    fun `detects circular dependency`() = withContext { ctx ->
        val exception = assertThrows(Exception::class.java) {
            ctx.evalBridje("""
                ns: consumer
                  require:
                    testlib:
                      circular_a
                42
            """.trimIndent())
        }
        assertTrue(exception.message?.contains("Circular dependency") == true)
    }

    @Test
    fun `prefers in-memory namespace over classpath`() = withContext { ctx ->
        // First define testlib:base in-memory with different value
        ctx.evalBridje("""
            ns: testlib:base
            def: value 100
        """.trimIndent())

        // Now require it - should use in-memory version, not classpath
        val result = ctx.evalBridje("""
            ns: consumer
              require:
                testlib:
                  base
            testlib:base/value
        """.trimIndent())
        assertEquals(100L, result.asLong())
    }

    @Test
    fun `errors when namespace not found on classpath`() = withContext { ctx ->
        val exception = assertThrows(Exception::class.java) {
            ctx.evalBridje("""
                ns: consumer
                  require:
                    nonexistent:
                      lib
                42
            """.trimIndent())
        }
        assertTrue(exception.message?.contains("not found on classpath") == true)
    }

    @Test
    fun `multiple requires from classpath`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: consumer
              require:
                testlib:
                  base
                  dependent
            add(testlib:base/value, testlib:dependent/doubled)
        """.trimIndent())
        // base.value = 42, dependent.doubled = 84, total = 126
        assertEquals(126L, result.asLong())
    }
}
