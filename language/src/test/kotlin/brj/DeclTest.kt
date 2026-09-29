package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeclTest {

    private fun Context.varMeta(nsName: String, varName: String): Value = evalBridje(
"meta(Var('$nsName/$varName))"
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
            decl: .name Str
            tag: User{.name}
            decl: user User
            def: user User{.name "James"}
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.tag", "user")
        assertTrue(meta.hasMember("declaredType"))
        val declType = meta.getMember("declaredType")
        assertEquals("User", declType.displayString())
    }

    @Test
    fun `decl tag type known to carry more keys than its own`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.tagkeys
            decl: .email Str
            decl: .name Str
            tag: User{.name}
            decl: user User{.name, .email}
            def: user User{.name "James", .email "j@example.com"}
        """.trimIndent())
        val declType = ctx.varMeta("test.decl.tagkeys", "user").getMember("declaredType")
        assertEquals("User{test.decl.tagkeys/.email}", declType.displayString())
    }

    @Test
    fun `a tag declared to carry a key must be constructed with it`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                ns: test.decl.tagkeys2
                decl: .email Str
                decl: .name Str
                tag: User{.name}
                decl: user User{.email}
                def: user User{.name "James"}
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("lacks {test.decl.tagkeys2/.email}") == true, "got: ${ex.message}")
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
        assertEquals("[a] Fn([a] a)", declType.displayString())
    }

    @Test
    fun `decl record type is its key set`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.rec
            decl: .name Str
            decl: user {.name}
            def: user {.name "James"}
        """.trimIndent())
        val declType = ctx.varMeta("test.decl.rec", "user").getMember("declaredType")
        assertEquals("{test.decl.rec/.name}", declType.displayString())
    }

    @Test
    fun `decl record type on a base`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.based
            decl: .dirty Bool
            decl: [a] same({.dirty & a}) {.dirty & a}
            def: same(r) r
        """.trimIndent())
        val declType = ctx.varMeta("test.decl.based", "same").getMember("declaredType")
        assertEquals("[a] Fn([{test.decl.based/.dirty & a}] {test.decl.based/.dirty & a})", declType.displayString())
    }

    @Test
    fun `decl of a record in and the same record out, carrying a key`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.decl.touch
            decl: .dirty Bool
            decl: [a] touch({& a}) {.dirty & a}
            def: touch(r) with(r, .dirty true)
        """.trimIndent())
        val declType = ctx.varMeta("test.decl.touch", "touch").getMember("declaredType")
        assertEquals("[a] Fn([{& a}] {test.decl.touch/.dirty & a})", declType.displayString())
    }

    @Test
    fun `decl of the same record out rejects a definition returning a new one`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                ns: test.decl.touch2
                decl: .dirty Bool
                decl: [a] touch({& a}) {.dirty & a}
                def: touch(r) {.dirty true}
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("more general than the definition") == true, "got: ${ex.message}")
    }

    @Test
    fun `a declared variable is rejected where the definition demands a type of it`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                ns: test.decl.rigid1
                decl: [a] f(a) Bool
                def: f(x) not(x)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("more general than the definition") == true, "got: ${ex.message}")
    }

    @Test
    fun `two declared variables are not one`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                ns: test.decl.rigid2
                decl: [a, b] f(a) b
                def: f(x) x
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("more general than the definition") == true, "got: ${ex.message}")
    }

    @Test
    fun `a record type's base is a type variable`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                ns: test.decl.badbase
                decl: .dirty Bool
                decl: x {.dirty & Int}
                def: x {.dirty true}
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("a record type's base is a type variable") == true, "got: ${ex.message}")
    }

    @Test
    fun `decl declares a key with a type variable`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: [a] .value a
              .value({.value 42})
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `a key's type is a scheme, instantiated at each use`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .email Str
              decl: [a] .greet Fn([{.email & a}] Str)
              def: greeting(r) .email(r)
              let: [greet .greet({.greet greeting})]
                greet({.email "hi"})
        """.trimIndent())
        assertEquals("hi", result.asString())
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
            decl: .test Bool
            decl: x Int
            ^.test
            def: x 42
        """.trimIndent())
        val meta = ctx.varMeta("test.decl.meta", "x")
        assertTrue(meta.hasMember("declaredType"))
        assertTrue(meta.hasMember("test"))
        assertEquals(true, meta.getMember("test").asBoolean())
    }
}
