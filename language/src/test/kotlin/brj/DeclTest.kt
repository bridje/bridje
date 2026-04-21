package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeclTest {

    private fun Context.varMeta(nsName: String, varName: String): Value = evalBridje(
        """meta(first(filterv(ns-vars(Symbol("$nsName")), fn: p(v) eq(nth(v, 1), Symbol("$varName")))))"""
    )

    private fun Value.displayString(): String = toString()

    @Test
    fun `decl value type`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl
            decl: x Int
            def: x 42
        """.trimIndent())
        val meta = ctx.varMeta("test.decl", "x")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Int", declType.displayString())
    }

    @Test
    fun `decl function type`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.fn
            decl: foo(Int, Str) Bool
            def: foo(a, b) true
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.fn", "foo")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Fn([Int, Str] Bool)", declType.displayString())
    }

    @Test
    fun `decl nullable type`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.nullable
            decl: name Str?
            def: name nil
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.nullable", "name")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Str?", declType.displayString())
    }

    @Test
    fun `decl vector type`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.vec
            decl: nums [Int]
            def: nums [1, 2, 3]
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.vec", "nums")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("[Int]", declType.displayString())
    }

    @Test
    fun `decl fn type value`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.fnval
            decl: callback Fn([Int] Bool)
            def: callback(x) true
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.fnval", "callback")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("Fn([Int] Bool)", declType.displayString())
    }

    @Test
    fun `decl tag type`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.tag
            tag: User(name)
            decl: user User
            def: user User("James")
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.tag", "user")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("test.decl.tag.User", declType.displayString())
    }

    @Test
    fun `decl without def does not error`() = withContext { ctx ->
        val ns = ctx.evalBridje("""
            ns: test.decl.pending
            decl: x Int
        """.trimIndent())
        assertNotNull(ns)
    }

    @Test
    fun `def without decl has no declaredType in meta`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.noDecl
            def: x 42
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.noDecl", "x")
        assertFalse(meta.hasMember("declaredType"))
    }

    @Test
    fun `decl polymorphic function type`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.poly
            decl: [a] identity(a) a
            def: identity(x) x
        """.trimIndent())
        val declType = ctx.varMeta("test.decl.poly", "identity").getMember("declaredType")
        assertEquals("Fn([?] ?)", declType.displayString())
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
        ctx.evalBridje("""
            ns: test.decl.meta
            decl: :test Bool
            decl: x Int
            ^:test
            def: x 42
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.meta", "x")
        assertTrue(meta.hasMember("declaredType"))
        assertTrue(meta.hasMember("test"))
        assertEquals(true, meta.getMember("test").asBoolean())
    }
}
