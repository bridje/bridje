package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TagTest {
    @Test
    fun `tag creates constructor in scope`() = withContext { ctx ->
        val constructor = ctx.evalBridje("decl: .value Int\ntag: Just{.value}")
        assertTrue(constructor.canExecute())
        assertEquals("Just", constructor.toString())
    }

    @Test
    fun `a tag is constructed from a record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              def: r {.value 42}
              Just(r)
        """.trimIndent())
        assertFalse(result.hasArrayElements())
        assertEquals(42L, result.getMember("value").asLong())
    }

    @Test
    fun `curly-brace sugar constructs a tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              .value(Just{.value 42})
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `a parenthesised record literal is the same call as the curly-brace sugar`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              case: Just({.value 42})
                Just({value}) value
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `tag Nothing creates singleton value`() = withContext { ctx ->
        val singleton = ctx.evalBridje("tag: Nothing")
        assertFalse(singleton.canExecute())
        assertFalse(singleton.canInstantiate())
        assertFalse(singleton.hasArrayElements())
        assertEquals("Nothing", singleton.toString())
    }

    @Test
    fun `Nothing is a singleton - same identity`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Nothing
              Nothing
        """.trimIndent())
        assertFalse(result.hasArrayElements())
        assertEquals("Nothing", result.toString())
    }

    @Test
    fun `nullary tag display string is just the tag name`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              tag: Nothing
              Nothing
        """.trimIndent())
        assertEquals("Nothing", result.toString())
    }

    @Test
    fun `tag display string is the tag and its record`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              Just{.value 42}
        """.trimIndent())
        assertEquals("Just{.value 42}", result.toString())
    }

    @Test
    fun `multi-key tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .fst Int
              decl: .snd Int
              tag: Pair{.fst, .snd}
              Pair{.fst 1, .snd 2}
        """.trimIndent())
        assertEquals(1L, result.getMember("fst").asLong())
        assertEquals(2L, result.getMember("snd").asLong())
    }

    @Test
    fun `a tag's record may carry keys beyond the tag's`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .email Str
              decl: .name Str
              tag: User{.name}
              .email(User{.name "James", .email "j@example.com"})
        """.trimIndent())
        assertEquals("j@example.com", result.asString())
    }

    @Test
    fun `a tag's record must carry the tag's keys`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: .fst Int
                  decl: .snd Int
                  tag: Pair{.fst, .snd}
                  Pair{.fst 1}
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("lacks {.snd}") == true, "got: ${ex.message}")
    }

    @Test
    fun `a tag is not constructed from anything but a record`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: .value Int
                  tag: Just{.value}
                  def: x 42
                  Just(x)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("Int is not a subtype of {.value}") == true, "got: ${ex.message}")
    }

    @Test
    fun `a tag takes one argument`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: .fst Int
                  decl: .snd Int
                  tag: Pair{.fst, .snd}
                  Pair(1, 2)
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("rity") == true, "Expected arity error, got: ${ex.message}")
    }

    @Test
    fun `positional tag declarations are rejected`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("tag: Pair(fst, snd)")
        }
        assertTrue(ex.message?.contains("a tag's payload is one record") == true, "got: ${ex.message}")
    }

    @Test
    fun `tag name must be capitalized`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("tag: just{.value}")
        }
        assertTrue(ex.message?.contains("capitalized") == true,
            "Expected capitalization error, got: ${ex.message}")
    }

    @Test
    fun `nested tags`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: [a] .value a
              tag: [a] Just{.value(a)}
              .value(.value(Just{.value Just{.value 42}}))
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `with keeps a tagged value's tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .name Str
              tag: User{.name}
              with(User{.name "a"}, .name "b")
        """.trimIndent())
        assertEquals("User", result.metaObject.metaSimpleName)
        assertEquals("b", result.getMember("name").asString())
    }

    @Test
    fun `a tag pattern binds the tagged value itself`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              case: Just{.value 1}
                Just(r) with(r, .value 2)
        """.trimIndent())
        assertEquals("Just{.value 2}", result.toString())
    }

    @Test
    fun `tagging a tagged value gives a new value, and the original keeps its tag`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .term Int
              tag: Leader{.term}
              tag: Follower{.term}
              let: [l Leader{.term 1}]
                [l, Follower(l)]
        """.trimIndent())
        assertEquals("Leader", result.getArrayElement(0).metaObject.metaSimpleName)
        assertEquals("Follower", result.getArrayElement(1).metaObject.metaSimpleName)
        assertEquals(1L, result.getArrayElement(1).getMember("term").asLong())
    }

    @Test
    fun `an anonymous record matches no tag`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("""
                do:
                  decl: .value Int
                  tag: Just{.value}
                  case: {.value 1}
                    Just 1
            """.trimIndent())
        }
        assertTrue(ex.message?.contains("{.value} is not a subtype of Just") == true, "got: ${ex.message}")
    }

    @Test
    fun `record keys resolve among the tag's own keys first`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: tag_keys_test
            decl: .fn Str
            decl: .ln Str
            tag: User{.fn, .ln}
        """.trimIndent())

        val ns = ctx.evalBridje("""
            ns: tag_keys_user
            def: result tag_keys_test/User{.fn "James", .ln "Henderson"}
        """.trimIndent())
        assertEquals("James", ns.getMember("result").getMember("fn").asString())
    }

    // Interop tests

    @Test
    fun `constructor is executable and instantiable`() = withContext { ctx ->
        val constructor = ctx.evalBridje("decl: .value Int\ntag: Just{.value}")
        assertTrue(constructor.canExecute())
        assertTrue(constructor.canInstantiate())
    }

    @Test
    fun `can instantiate using constructor`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: tag_interop_test
            decl: .value Int
            tag: Just{.value}
        """.trimIndent())
        val constructor = ctx.evalBridje("tag_interop_test/Just")
        val result = constructor.newInstance(ctx.evalBridje("{tag_interop_test/.value 42}"))
        assertEquals(42L, result.getMember("value").asLong())
        assertEquals(42L, result.getMember("tag_interop_test/value").asLong())
    }

    @Test
    fun `tagged record has meta object`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: .value Int
              tag: Just{.value}
              Just{.value 42}
        """.trimIndent())
        val meta = result.metaObject
        assertNotNull(meta)
        assertEquals("Just", meta.metaSimpleName)
        assertEquals("Just", meta.metaQualifiedName)
    }

    @Test
    fun `meta object isMetaInstance works`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: tag_meta_test
            decl: .value Int
            tag: Just{.value}
            tag: Other{.value}
        """.trimIndent())

        val justConstructor = ctx.evalBridje("tag_meta_test/Just")
        val otherConstructor = ctx.evalBridje("tag_meta_test/Other")
        val justValue = ctx.evalBridje("tag_meta_test/Just{.value 42}")
        val otherValue = ctx.evalBridje("tag_meta_test/Other{.value 42}")

        assertTrue(justConstructor.isMetaInstance(justValue))
        assertFalse(justConstructor.isMetaInstance(otherValue))
        assertTrue(otherConstructor.isMetaInstance(otherValue))
        assertFalse(otherConstructor.isMetaInstance(justValue))
    }

    @Test
    fun `singleton is its own meta object`() = withContext { ctx ->
        val singleton = ctx.evalBridje("tag: Nothing")
        val meta = singleton.metaObject
        assertNotNull(meta)
        assertEquals("Nothing", meta.metaSimpleName)
        assertEquals("Nothing", meta.metaQualifiedName)
        assertTrue(meta.isMetaInstance(singleton))
    }

    @Test
    fun `constructor is the tagged record's meta object`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: tag_record_test
            decl: .value Int
            tag: Just{.value}
        """.trimIndent())

        val tagged = ctx.evalBridje("tag_record_test/Just{.value 42}")
        val meta = tagged.metaObject

        assertTrue(meta.canExecute())
        assertTrue(meta.isMetaInstance(tagged))
    }

    @Test
    fun `parameterised tag with type variable`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: [a] .value a
              tag: [t] Box{.value(t)}
              case: Box{.value 42}
                Box{value} value
        """.trimIndent())
        assertEquals(42, result.asInt())
    }

    @Test
    fun `parameterised tag preserves type identity`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: [a] .value a
              tag: [t] Wrapper{.value(t)}
              decl: [t] unwrap(Wrapper(t)) t
              def: unwrap(w) case: w
                Wrapper(r) .value(r)
              unwrap(Wrapper{.value "hello"})
        """.trimIndent())
        assertEquals("hello", result.asString())
    }

    @Test
    fun `a tag instantiates a key declared with a type variable`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            do:
              decl: [a] .value a
              decl: [a] .error a
              enum: Result(a, e)
                tag: Ok{.value(a)}
                tag: Err{.error(e)}
              case: Ok{.value 42}
                Ok{value} value
                Err 0
        """.trimIndent())
        assertEquals(42L, result.asLong())
    }

    @Test
    fun `a key's type argument must be the tag's type variable`() = withContext { ctx ->
        val ex = assertThrows(PolyglotException::class.java) {
            ctx.evalBridje("decl: [a] .value a\ntag: [t] Box{.value(u)}")
        }
        assertTrue(ex.message?.contains("u") == true, "got: ${ex.message}")
    }
}
