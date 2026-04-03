package brj

import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeclTest {

    private fun Value.varMeta(name: String): Value = getMember("__var_meta__:$name")

    private fun Value.displayString(): String = toString()

    @Test
    fun `decl value type`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl
            decl: x Int
            def: x 42
        """.trimIndent())
        val meta = ns.varMeta("x")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Int", declType.displayString())
    }

    @Test
    fun `decl function type`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:fn
            decl: foo(Int, Str) Bool
            def: foo(a, b) true
        """.trimIndent())
        val meta = ns.varMeta("foo")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Fn([Int, Str] Bool)", declType.displayString())
    }

    @Test
    fun `decl nullable type`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:nullable
            decl: name Str?
            def: name nil
        """.trimIndent())
        val meta = ns.varMeta("name")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Str?", declType.displayString())
    }

    @Test
    fun `decl vector type`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:vec
            decl: nums [Int]
            def: nums [1, 2, 3]
        """.trimIndent())
        val meta = ns.varMeta("nums")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("[Int]", declType.displayString())
    }

    @Test
    fun `decl fn type value`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:fnval
            decl: callback Fn([Int] Bool)
            def: callback(x) true
        """.trimIndent())
        val meta = ns.varMeta("callback")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Fn([Int] Bool)", declType.displayString())
    }

    @Test
    fun `decl tag type`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:tag
            deftag: User(name)
            decl: user User
            def: user User("James")
        """.trimIndent())
        val meta = ns.varMeta("user")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("test:decl:tag:User", declType.displayString())
    }

    @Test
    fun `decl without def does not error`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:pending
            decl: x Int
        """.trimIndent())
        assertNotNull(ns)
    }

    @Test
    fun `def without decl has no declaredType in meta`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:noDecl
            def: x 42
        """.trimIndent())
        val meta = ns.varMeta("x")
        assertFalse(meta.hasMember("declaredType"))
    }

    @Test
    fun `decl in value position is an error`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  let: [x decl: y Int]
                    x
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("decl not allowed in value position") == true,
            "Expected 'decl not allowed in value position', got: ${ex.message}")
    }

    @Test
    fun `decl preserves existing meta`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test:decl:meta
            defkeys: {.test Bool}
            decl: x Int
            ^.test
            def: x 42
        """.trimIndent())
        val meta = ns.varMeta("x")
        assertTrue(meta.hasMember("declaredType"))
        assertTrue(meta.hasMember("test"))
        assertEquals(true, meta.getMember("test").asBoolean())
    }
}
