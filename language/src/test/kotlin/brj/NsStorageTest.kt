package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NsStorageTest {
    @Test
    fun `ns stores NsDecl and forms`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test:storage
            def: x 42
            def: y 100
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        val ns = bindings.getMember("test:storage")
        
        // Verify the namespace exists
        assertNotNull(ns)
        assertTrue(ns.hasMember("x"))
        assertTrue(ns.hasMember("y"))
        
        // Verify values
        assertEquals(42L, ns.getMember("x").asLong())
        assertEquals(100L, ns.getMember("y").asLong())
    }
    
    @Test
    fun `ns with requires stores dependency info`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: lib:base
            def: value 10
        """.trimIndent())
        
        ctx.evalBridje("""
            ns: lib:dependent
              require:
                lib:
                  base
            def: doubled lib:base/value
        """.trimIndent())
        
        val bindings = ctx.getBindings("bridje")
        val dependentNs = bindings.getMember("lib:dependent")
        
        assertNotNull(dependentNs)
        assertTrue(dependentNs.hasMember("doubled"))
        assertEquals(10L, dependentNs.getMember("doubled").asLong())
    }
}
