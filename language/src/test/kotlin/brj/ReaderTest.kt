package brj

import brj.Reader.Companion.readForms
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

private fun Form.contentEquals(other: Form): Boolean = when {
    this is IntForm && other is IntForm -> value == other.value
    this is DoubleForm && other is DoubleForm -> value == other.value
    this is BigIntForm && other is BigIntForm -> value == other.value
    this is BigDecForm && other is BigDecForm -> value == other.value
    this is StringForm && other is StringForm -> value == other.value
    this is SymbolForm && other is SymbolForm -> name == other.name
    this is QualifiedSymbolForm && other is QualifiedSymbolForm -> namespace == other.namespace && member == other.member
    this is KeywordForm && other is KeywordForm -> name == other.name
    this is ListForm && other is ListForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    this is VectorForm && other is VectorForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    this is SetForm && other is SetForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    this is RecordForm && other is RecordForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    else -> false
}

private fun sym(name: String) = SymbolForm(name)
private fun qsym(ns: String, member: String) = QualifiedSymbolForm(ns, member)
private fun kw(name: String) = KeywordForm(name)
private fun int(value: Long) = IntForm(value)
private fun list(vararg els: Form) = ListForm(els.toList())
private fun vec(vararg els: Form) = VectorForm(els.toList())

class ReaderTest {
    private fun String.readSingle(): Form =
        Source.newBuilder("bridje", this, "test.brj").build().readForms().single()

    private fun assertReads(expected: Form, actual: Form) {
        assertTrue(expected.contentEquals(actual), "Expected $expected but got $actual")
    }

    @Test
    fun `reads symbol`() = assertReads(sym("foo"), "foo".readSingle())

    @Test
    fun `reads int`() = assertReads(int(42), "42".readSingle())

    @Test
    fun `reads float`() = assertReads(DoubleForm(3.14), "3.14".readSingle())

    @Test
    fun `reads bigint`() = assertReads(BigIntForm(BigInteger("12345678901234567890")), "12345678901234567890N".readSingle())

    @Test
    fun `reads bigdec`() = assertReads(BigDecForm(BigDecimal("3.14159265358979323846")), "3.14159265358979323846M".readSingle())

    @Test
    fun `reads string`() = assertReads(StringForm("hello"), "\"hello\"".readSingle())

    @Test
    fun `reads list`() = assertReads(list(sym("foo"), sym("bar")), "(foo bar)".readSingle())

    @Test
    fun `reads vector`() = assertReads(vec(int(1), int(2), int(3)), "[1 2 3]".readSingle())

    @Test
    fun `reads call`() = assertReads(list(sym("foo"), sym("a"), sym("b")), "foo(a, b)".readSingle())

    @Test
    fun `reads method call`() = assertReads(list(sym("foo"), sym("x"), sym("a")), "x.foo(a)".readSingle())

    @Test
    fun `reads field access`() = assertReads(list(kw("foo"), sym("x")), "x.foo".readSingle())

    @Test
    fun `reads simple block`() =
        assertReads(
            list(sym("def"), list(sym("foo")), list(sym("bar"))),
            """
            def: foo()
              bar()
            """.trimIndent().readSingle()
        )

    @Test
    fun `reads block with multiple body forms`() =
        assertReads(
            list(sym("def"), list(sym("foo")), list(sym("bar")), list(sym("baz"))),
            """
            def: foo()
              bar()
              baz()
            """.trimIndent().readSingle()
        )

    @Test
    fun `reads nested blocks`() =
        assertReads(
            list(sym("def"), list(sym("foo")), list(sym("let"), vec(sym("x"), int(1)), list(sym("bar"), sym("x")))),
            """
            def: foo()
              let: [x 1]
                bar(x)
            """.trimIndent().readSingle()
        )

    @Test
    fun `reads inline block (no body)`() =
        assertReads(
            list(sym("if"), sym("pred"), sym("then"), sym("else")),
            "if: pred then else".readSingle()
        )

    @Test
    fun `reads do block`() =
        assertReads(
            list(sym("do"), list(sym("foo")), list(sym("bar"))),
            """
            do:
              foo()
              bar()
            """.trimIndent().readSingle()
        )

    @Test
    fun `reads qualified symbol`() =
        assertReads(qsym("java:time:Instant", "now"), "java:time:Instant/now".readSingle())

    @Test
    fun `reads simple qualified symbol`() =
        assertReads(qsym("foo", "bar"), "foo/bar".readSingle())

    @Test
    fun `reads qualified symbol call`() =
        assertReads(
            list(qsym("java:time:Instant", "now")),
            "java:time:Instant/now()".readSingle()
        )

    // Helper function for location tests - uses first() instead of single() 
    // since we're testing a single form but want to access its location properties
    private fun String.readWithLocation(): Form =
        Source.newBuilder("bridje", this, "test.brj").build().readForms().first()

    // Location tests for blocks - verifying extents don't include trailing whitespace
    @Test
    fun `simple block location excludes trailing whitespace`() {
        val source = """
            def: foo()
              bar()
            baz()
        """.trimIndent()
        val form = source.readWithLocation()
        
        // The block_call should end at bar()'s closing paren, not include newline after
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        val expected = "def: foo()\n  bar()"
        assertTrue(loc.charIndex == 0, "Block should start at position 0, got ${loc.charIndex}")
        assertTrue(loc.charLength == expected.length, "Block should end at bar()'s closing paren (length ${expected.length}), got ${loc.charLength}")
        assertTrue(loc.characters.toString() == expected, "Block extent should be '$expected', got '${loc.characters}'")
    }

    @Test
    fun `nested block locations exclude trailing whitespace`() {
        val source = """
            def: outer()
              if: condition
                inner()
              after()
            next()
        """.trimIndent()
        val form = source.readWithLocation()
        
        // The outer block_call should end at after()'s closing paren
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        val expected = "def: outer()\n  if: condition\n    inner()\n  after()"
        assertTrue(loc.charIndex == 0, "Outer block should start at position 0, got ${loc.charIndex}")
        assertTrue(loc.charLength == expected.length, "Block length should be ${expected.length}, got ${loc.charLength}")
        assertTrue(loc.characters.toString() == expected, 
            "Outer block extent should not include trailing newline.\nExpected: '$expected'\nGot: '${loc.characters}'")
    }

    @Test
    fun `block with blank line in body location`() {
        val source = """
            def: foo()
              bar()

              baz()
            qux()
        """.trimIndent()
        val form = source.readWithLocation()
        
        // The block should include the blank line but not trailing whitespace after baz()
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        val expected = "def: foo()\n  bar()\n\n  baz()"
        assertTrue(loc.charIndex == 0, "Block should start at position 0, got ${loc.charIndex}")
        assertTrue(loc.charLength == expected.length, "Block length should be ${expected.length}, got ${loc.charLength}")
        assertTrue(loc.characters.toString() == expected,
            "Block with blank line should not include trailing whitespace.\nExpected: '$expected'\nGot: '${loc.characters}'")
    }

    @Test
    fun `block with blank line before dedent location`() {
        val source = """
            def: foo()
              bar()

            baz()
        """.trimIndent()
        val form = source.readWithLocation()
        
        // The block should include blank line but not the trailing newline after it
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        val expected = "def: foo()\n  bar()\n"
        assertTrue(loc.charIndex == 0, "Block should start at position 0, got ${loc.charIndex}")
        assertTrue(loc.charLength == expected.length, "Block length should be ${expected.length}, got ${loc.charLength}")
        assertTrue(loc.characters.toString() == expected,
            "Block with blank line before dedent.\nExpected: '$expected'\nGot: '${loc.characters}'")
    }

    @Test
    fun `do block at EOF location`() {
        val source = """
            do:
              foo()
              bar()
        """.trimIndent()
        val form = source.readWithLocation()
        
        // The block at EOF should end at bar()'s closing paren
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        val expected = "do:\n  foo()\n  bar()"
        assertTrue(loc.charIndex == 0, "Block should start at position 0, got ${loc.charIndex}")
        assertTrue(loc.charLength == expected.length, "Block length should be ${expected.length}, got ${loc.charLength}")
        assertTrue(loc.characters.toString() == expected,
            "Block at EOF should end at last content.\nExpected: '$expected'\nGot: '${loc.characters}'")
    }
}
