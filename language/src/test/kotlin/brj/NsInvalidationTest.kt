package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NsInvalidationTest {
    @Test
    fun `re-evaluating namespace invalidates dependents`() = withContext { ctx ->
        // Create a base namespace
        ctx.evalBridje("""
            ns: lib:base
            def: value 10
        """.trimIndent())
        
        // Create a dependent namespace
        ctx.evalBridje("""
            ns: lib:dependent
              require:
                lib:
                  base
            def: doubled add(lib:base/value, lib:base/value)
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        
        // Both namespaces should be available
        assertTrue(bindings.hasMember("lib:base"))
        assertTrue(bindings.hasMember("lib:dependent"))
        
        val dependentNs = bindings.getMember("lib:dependent")
        assertEquals(20L, dependentNs.getMember("doubled").asLong())
        
        // Re-evaluate the base namespace with a different value
        ctx.evalBridje("""
            ns: lib:base
            def: value 20
        """.trimIndent())
        
        // Check that base namespace is still available with new value
        val baseNs = bindings.getMember("lib:base")
        assertEquals(20L, baseNs.getMember("value").asLong())
        
        // The dependent namespace should be invalidated (removed from scope)
        assertFalse(bindings.hasMember("lib:dependent"), 
            "Dependent namespace should be invalidated when its dependency is re-evaluated")
    }
    
    @Test
    fun `invalidation is recursive`() = withContext { ctx ->
        // Create a chain: base -> middle -> top
        ctx.evalBridje("""
            ns: lib:base
            def: value 10
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
            def: tripled add(lib:middle/doubled, lib:base/value)
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        
        // All namespaces should be available
        assertTrue(bindings.hasMember("lib:base"))
        assertTrue(bindings.hasMember("lib:middle"))
        assertTrue(bindings.hasMember("lib:top"))
        
        // Re-evaluate base
        ctx.evalBridje("""
            ns: lib:base
            def: value 20
        """.trimIndent())
        
        // Base should still be there
        assertTrue(bindings.hasMember("lib:base"))
        
        // Both middle and top should be invalidated
        assertFalse(bindings.hasMember("lib:middle"), 
            "Middle namespace should be invalidated")
        assertFalse(bindings.hasMember("lib:top"), 
            "Top namespace should be invalidated recursively")
    }
    
    @Test
    fun `namespace without dependents can be re-evaluated`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: lib:standalone
            def: value 10
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        assertTrue(bindings.hasMember("lib:standalone"))
        assertEquals(10L, bindings.getMember("lib:standalone").getMember("value").asLong())
        
        // Re-evaluate
        ctx.evalBridje("""
            ns: lib:standalone
            def: value 20
        """.trimIndent())
        
        // Should still be available with new value
        assertTrue(bindings.hasMember("lib:standalone"))
        assertEquals(20L, bindings.getMember("lib:standalone").getMember("value").asLong())
    }
}
