package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NsReEvaluationTest {
    @Test
    fun `quarantined dependency is re-evaluated when needed`() = withContext { ctx ->
        // Create base namespace
        ctx.evalBridje("""
            ns: lib:base
            def: value 10
        """.trimIndent())
        
        // Create dependent namespace
        ctx.evalBridje("""
            ns: lib:dependent
              require:
                lib:
                  base
            def: doubled add(lib:base/value, lib:base/value)
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        
        // Both should be available
        assertTrue(bindings.hasMember("lib:base"))
        assertTrue(bindings.hasMember("lib:dependent"))
        assertEquals(20L, bindings.getMember("lib:dependent").getMember("doubled").asLong())
        
        // Re-evaluate base, which invalidates dependent
        ctx.evalBridje("""
            ns: lib:base
            def: value 20
        """.trimIndent())
        
        // Only base should be available now
        assertTrue(bindings.hasMember("lib:base"))
        assertFalse(bindings.hasMember("lib:dependent"))
        
        // Now re-evaluate dependent - it should automatically re-evaluate base first
        ctx.evalBridje("""
            ns: lib:dependent
              require:
                lib:
                  base
            def: doubled add(lib:base/value, lib:base/value)
        """.trimIndent())
        
        // Dependent should be available again with updated value
        assertTrue(bindings.hasMember("lib:dependent"))
        assertEquals(40L, bindings.getMember("lib:dependent").getMember("doubled").asLong())
    }
    
    @Test
    fun `re-evaluation happens in dependency order`() = withContext { ctx ->
        // Create chain: base -> middle -> top
        ctx.evalBridje("""
            ns: lib:base
            def: value 5
        """.trimIndent())
        
        ctx.evalBridje("""
            ns: lib:middle
              require:
                lib:
                  base
            def: doubled add(lib:base/value, lib:base/value)
        """.trimIndent())
        
        ctx.evalBridje("""
            ns: lib:top
              require:
                lib:
                  middle
                  base
            def: sum add(lib:middle/doubled, lib:base/value)
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        
        // All should be available
        assertTrue(bindings.hasMember("lib:base"))
        assertTrue(bindings.hasMember("lib:middle"))
        assertTrue(bindings.hasMember("lib:top"))
        assertEquals(15L, bindings.getMember("lib:top").getMember("sum").asLong())
        
        // Re-evaluate base, invalidating middle and top
        ctx.evalBridje("""
            ns: lib:base
            def: value 10
        """.trimIndent())
        
        assertFalse(bindings.hasMember("lib:middle"))
        assertFalse(bindings.hasMember("lib:top"))
        
        // Re-evaluate top - should automatically re-evaluate base and middle first
        ctx.evalBridje("""
            ns: lib:top
              require:
                lib:
                  middle
                  base
            def: sum add(lib:middle/doubled, lib:base/value)
        """.trimIndent())
        
        // All should be available again with correct values
        assertTrue(bindings.hasMember("lib:base"))
        assertTrue(bindings.hasMember("lib:middle"))
        assertTrue(bindings.hasMember("lib:top"))
        
        // base=10, middle=20, top=30
        assertEquals(10L, bindings.getMember("lib:base").getMember("value").asLong())
        assertEquals(20L, bindings.getMember("lib:middle").getMember("doubled").asLong())
        assertEquals(30L, bindings.getMember("lib:top").getMember("sum").asLong())
    }
    
    @Test
    fun `re-evaluation halts on first failure`() = withContext { ctx ->
        // Create base namespace
        ctx.evalBridje("""
            ns: lib:base
            def: value 10
        """.trimIndent())
        
        // Create dependent that would fail if base changes
        ctx.evalBridje("""
            ns: lib:dependent
              require:
                lib:
                  base
            def: result lib:base/value
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        assertTrue(bindings.hasMember("lib:dependent"))
        
        // Re-evaluate base, removing the value field (would cause failure)
        ctx.evalBridje("""
            ns: lib:base
            def: other 20
        """.trimIndent())
        
        assertFalse(bindings.hasMember("lib:dependent"))
        
        // Try to re-evaluate dependent - should fail because base doesn't have value anymore
        // But the quarantined version of base had value, so re-evaluation of quarantined base
        // should succeed, then the new evaluation of dependent should fail
        try {
            ctx.evalBridje("""
                ns: lib:dependent
                  require:
                    lib:
                      base
                def: result lib:base/value
            """.trimIndent())
            fail("Expected evaluation to fail")
        } catch (e: Exception) {
            // Expected - lib:base/value doesn't exist in the new version
            assertTrue((e.message?.contains("value") ?: false) || 
                      (e.message?.contains("not found") ?: false))
        }
    }
}
