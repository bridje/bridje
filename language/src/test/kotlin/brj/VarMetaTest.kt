package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VarMetaTest {

    @Test
    fun `varMeta returns empty record for a builtin without user meta or loc`() = withContext { ctx ->
        // brj.core/add is a builtin — no source location recorded at register time.
        val meta = ctx.evalBridje("varMeta(`add)")
        assertTrue(meta.hasMembers())
        assertFalse(meta.hasMember("loc"))
    }

    @Test
    fun `varMeta returns loc for a user-defined def`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.loc
            def: foo(x) x
        """.trimIndent())

        val meta = ctx.evalBridje("varMeta(`test.loc/foo)")
        assertTrue(meta.hasMembers(), "meta should have members")
        assertTrue(meta.hasMember("loc"), "meta should carry :loc")

        val loc = meta.getMember("loc")
        assertEquals("Loc", loc.metaObject.metaSimpleName)
        assertTrue(loc.hasMember("source"))
        assertTrue(loc.hasMember("startLine"))
        assertTrue(loc.hasMember("startColumn"))
        assertTrue(loc.hasMember("endLine"))
        assertTrue(loc.hasMember("endColumn"))

        val startLine = loc.getMember("startLine").asLong()
        assertTrue(startLine > 0, "startLine must be positive")
    }

    @Test
    fun `varMeta preserves user meta alongside loc`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.loc.user
            ^:test
            def: myTest nil
        """.trimIndent())

        val meta = ctx.evalBridje("varMeta(`test.loc.user/myTest)")
        assertTrue(meta.hasMember("test"), "user meta :test must be preserved")
        assertTrue(meta.hasMember("loc"), ":loc must be added")
        assertTrue(meta.getMember("test").asBoolean())
    }
}
