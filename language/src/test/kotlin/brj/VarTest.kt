package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VarTest {

    @Test
    fun `Var resolves a builtin to its GlobalVar`() = withContext { ctx ->
        val v = ctx.evalBridje("Var(`brj.core/add)")
        assertEquals("Var", v.metaObject.metaSimpleName)
        assertEquals("#'brj.core/add", v.toString())
    }

    @Test
    fun `Var destructures as (ns, name)`() = withContext { ctx ->
        val v = ctx.evalBridje("Var(`brj.core/add)")
        assertTrue(v.hasArrayElements())
        assertEquals(2L, v.arraySize)
        assertEquals("brj.core", v.getArrayElement(0).toString())
        assertEquals("add", v.getArrayElement(1).toString())
    }

    @Test
    fun `meta on a builtin Var has no loc`() = withContext { ctx ->
        // brj.core/add is a builtin — no source location recorded at register time.
        val meta = ctx.evalBridje("meta(Var(`brj.core/add))")
        assertTrue(meta.hasMembers())
        assertFalse(meta.hasMember("loc"))
    }

    @Test
    fun `meta on a user-defined def returns its loc`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.loc
            def: foo(x) x
        """.trimIndent())

        val meta = ctx.evalBridje("meta(Var(`test.loc/foo))")
        assertTrue(meta.hasMember("loc"), "meta should carry :loc")

        val loc = meta.getMember("loc")
        assertEquals("Loc", loc.metaObject.metaSimpleName)
        assertTrue(loc.getMember("start-line").asLong() > 0)
    }

    @Test
    fun `meta preserves user meta alongside loc`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.loc.user
              require:
                brj: as(test, t)

            ^:t/test
            def: myTest nil
        """.trimIndent())

        val meta = ctx.evalBridje("meta(Var(`test.loc.user/myTest))")
        assertTrue(meta.hasMember("test"), "user meta :test must be preserved")
        assertTrue(meta.hasMember("loc"), ":loc must be added")
        assertTrue(meta.getMember("test").asBoolean())
    }

}
