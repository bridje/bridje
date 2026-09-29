package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

// A key declared with type variables holds its value at an instance each record carries, so reading it gives
// that instance back, and a record whose type does not say it has no instance to give.
class PolymorphicKeyTest {
    private fun rejects(src: String, message: String) = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) { ctx.evalBridje(src.trimIndent()) }
        assertTrue(ex.message?.contains(message) == true, "got: ${ex.message}")
    }

    @Test
    fun `a key read from a literal is at the literal's instance`() = rejects("""
        do:
          decl: [a] .value a
          not(.value({.value 1}))
    """, "Int is not a subtype of Bool")

    @Test
    fun `a key read from a tag is at the tag's argument`() = rejects("""
        do:
          decl: [a] .value a
          tag: [a] Just{.value(a)}
          not(.value(Just{.value "s"}))
    """, "Str is not a subtype of Bool")

    @Test
    fun `a tag's binding reads its key at the tag's argument`() = rejects("""
        do:
          decl: [a] .value a
          enum: Maybe(a)
            tag: Just{.value(a)}
            tag: None
          case: Just{.value "s"}
            Just(r) not(.value(r))
            None true
    """, "Str is not a subtype of Bool")

    @Test
    fun `a tag argument a key takes as a parameter is contravariant`() = rejects("""
        ns: test.pkey.contra
        decl: [a] .h Fn([a] Bool)
        enum: E(a)
          tag: H{.h(a)}
          tag: G
        def: g(y)
          case: y
            H{h} h("str")
            G true
        def: x g(H{.h not})
    """, "Str is not a subtype of Bool")

    @Test
    fun `a key read at its instance gives it back`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: [a] .value a
              tag: [a] Just{.value(a)}
              .value(.value(Just{.value Just{.value 42}}))
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `an optional read of a key a closed record lacks is nil`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: [a] .value a
              decl: .other Int
              .?value({.other 1})
        """.trimIndent())
        assertTrue(result.isNull)
    }

    @Test
    fun `a join keeps the instance of a key only one side carries`() = rejects("""
        ns: test.pkey.join
        decl: [a] .value a
        decl: .other Int
        def: pick(p)
          if: p {.value 1} {.other 2}
        def: x not(.?value(pick(true)))
    """, "Int? may be nil")

    @Test
    fun `an optional read of a key an open record may carry at any type is an error`() = rejects("""
        ns: test.pkey.open
        decl: [a] .value a
        decl: .other Int
        decl: forget({.other}) {.other}
        def: forget(r) r
        def: x .?value(forget({.other 1, .value "s"}))
    """, "may carry test.pkey.open/.value at any type")

    @Test
    fun `a key is declared with a type`() = rejects("""
        do:
          decl: .value
    """, "needs a type")

    @Test
    fun `a tag's keys are declared beforehand`() = rejects("""
        do:
          tag: Just{.value}
    """, ".value has no declared type")

    @Test
    fun `a tag gives a key's type variables`() = rejects("""
        do:
          decl: [a] .value a
          tag: Just{.value}
    """, ".value is declared with type variables")

    // A bare `Maybe` has fresh arguments, which nothing in a key's declaration quantifies.
    @Test
    fun `a key's type names every type argument`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                decl: [a] .value a
                enum: Maybe(a)
                  tag: Just{.value(a)}
                  tag: None
                decl: .m Maybe
            """.trimIndent())
        }
        assertTrue(ex.message?.contains(".m's type leaves type arguments unnamed") == true, "got: ${ex.message}")
    }
}
