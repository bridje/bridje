package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NsClasspathLoadingTest {
    @Test
    fun `loads namespace from classpath`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test:user
              require:
                require_test:
                  base

            def: result require_test:base:value
        """.trimIndent())

        val bindings = ctx.getBindings("bridje")
        assertTrue(bindings.hasMember("test:user"))
        assertEquals(42L, bindings.getMember("test:user").getMember("result").asLong())
    }

    @Test
    fun `loads transitive dependencies from classpath`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test:user
              require:
                require_test:
                  dependent.as(dep)

            def: result dep:doubled
        """.trimIndent())

        val bindings = ctx.getBindings("bridje")
        assertTrue(bindings.hasMember("test:user"))
        assertEquals(84L, bindings.getMember("test:user").getMember("result").asLong())
    }

    @Test
    fun `detects circular dependency`() = withContext { ctx ->
        try {
            ctx.evalBridje("""
                ns: test:user
                  require:
                    require_test:
                      circular_a

                def: value 1
            """.trimIndent())
            fail("Expected circular dependency error")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Circular dependency") ?: false)
        }
    }

    @Test
    fun `error when namespace not found on classpath`() = withContext { ctx ->
        try {
            ctx.evalBridje("""
                ns: test:user
                  require:
                    nonexistent:
                      module

                def: value 1
            """.trimIndent())
            fail("Expected namespace not found error")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("not found") ?: false ||
                      e.message?.contains("nonexistent:module") ?: false)
        }
    }

    @Test
    fun `mixed in-memory and classpath loading`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: inmemory:base

            def: value 100
        """.trimIndent())

        ctx.evalBridje("""
            ns: test:user
              require:
                inmemory:
                  base
                require_test:
                  base

            def: sum add(inmemory:base:value, require_test:base:value)
        """.trimIndent())

        val bindings = ctx.getBindings("bridje")
        assertTrue(bindings.hasMember("test:user"))
        assertEquals(142L, bindings.getMember("test:user").getMember("sum").asLong())
    }
}
