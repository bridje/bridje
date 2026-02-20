package brj

import brj.Reader.Companion.readForms
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Assertions.assertEquals
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
    this is KeywordForm && other is KeywordForm -> name == other.name
    this is QualifiedSymbolForm && other is QualifiedSymbolForm -> namespace == other.namespace && member == other.member
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
private fun kw(name: String) = KeywordForm(name)
private fun qsym(ns: String, member: String) = QualifiedSymbolForm(ns, member)
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
        assertReads(qsym("java:time:Instant", "now"), "java:time:Instant:now".readSingle())

    @Test
    fun `reads simple qualified symbol`() =
        assertReads(qsym("foo", "bar"), "foo:bar".readSingle())

    @Test
    fun `reads qualified symbol call`() =
        assertReads(
            list(qsym("java:time:Instant", "now")),
            "java:time:Instant:now()".readSingle()
        )

    private fun String.readWithLocation(): Form =
        Source.newBuilder("bridje", this, "test.brj").build().readForms().first()

    private fun String.readAllWithLocation(): List<Form> =
        Source.newBuilder("bridje", this, "test.brj").build().readForms().toList()

    // Location tests for blocks - verifying extents don't include trailing whitespace
    @Test
    fun `simple block location excludes trailing whitespace`() {
        val source = """
            def: foo()
              bar()
            baz()
        """.trimIndent()
        val form = source.readWithLocation()
        
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex, "Block should start at position 0")
        assertEquals(18, loc.charLength, "Block should end at bar()'s closing paren")
        assertEquals("def: foo()\n  bar()", loc.characters.toString())
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
        
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex, "Outer block should start at position 0")
        assertEquals(50, loc.charLength, "Outer block should end at after()'s closing paren")
        assertEquals("def: outer()\n  if: condition\n    inner()\n  after()", loc.characters.toString())
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
        
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex, "Block should start at position 0")
        assertEquals(27, loc.charLength, "Block should include blank line but not trailing whitespace")
        assertEquals("def: foo()\n  bar()\n\n  baz()", loc.characters.toString())
    }

    @Test
    fun `block with blank line before dedent location`() {
        val source = """
            def: foo()
              bar()

            baz()
        """.trimIndent()
        val form = source.readWithLocation()
        
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex, "Block should start at position 0")
        assertEquals(18, loc.charLength, "Block should not include blank line before dedent")
        assertEquals("def: foo()\n  bar()", loc.characters.toString())
    }

    @Test
    fun `do block at EOF location`() {
        val source = """
            do:
              foo()
              bar()
        """.trimIndent()
        val form = source.readWithLocation()
        
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex, "Block should start at position 0")
        assertEquals(19, loc.charLength, "Block at EOF should end at last content")
        assertEquals("do:\n  foo()\n  bar()", loc.characters.toString())
    }

    // Multi-level dedent: exercises the queued_dedent_target path in the scanner
    // where multiple DEDENT tokens are emitted in sequence
    @Test
    fun `multi-level dedent location`() {
        val source = """
            def: a()
              if: b
                c()
            d()
        """.trimIndent()
        val form = source.readWithLocation()

        val expected = "def: a()\n  if: b\n    c()"
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex)
        assertEquals(expected.length, loc.charLength)
        assertEquals(expected, loc.characters.toString())
    }

    // Block with args on the call line before the indented body
    @Test
    fun `block with args before body location`() {
        val source = """
            if: cond
              body()
            next()
        """.trimIndent()
        val form = source.readWithLocation()

        val expected = "if: cond\n  body()"
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex)
        assertEquals(expected.length, loc.charLength)
        assertEquals(expected, loc.characters.toString())
    }

    // Verify the second of two sequential blocks also has correct extents
    @Test
    fun `sequential blocks second block location`() {
        val source = """
            def: a()
              x()
            def: b()
              y()
        """.trimIndent()
        val forms = source.readAllWithLocation()

        val firstExpected = "def: a()\n  x()"
        val firstLoc = requireNotNull(forms[0].loc)
        assertEquals(0, firstLoc.charIndex)
        assertEquals(firstExpected.length, firstLoc.charLength)
        assertEquals(firstExpected, firstLoc.characters.toString())

        val secondExpected = "def: b()\n  y()"
        val secondLoc = requireNotNull(forms[1].loc)
        assertEquals(firstExpected.length + 1, secondLoc.charIndex, "Second block starts after newline")
        assertEquals(secondExpected.length, secondLoc.charLength)
        assertEquals(secondExpected, secondLoc.characters.toString())
    }

    // Block inside parentheses: exercises the closing-bracket dedent path.
    // Verifies the inner block_call extent ends at its body content, not at the closing paren.
    @Test
    fun `block inside parens inner block location`() {
        val source = """
            foo(if: x
              y())
        """.trimIndent()
        val form = source.readWithLocation() as ListForm
        val innerBlock = form.els[1] // the block_call form (if: x y())

        val expected = "if: x\n  y()"
        val loc = requireNotNull(innerBlock.loc) { "Inner block should have location information" }
        assertEquals(expected.length, loc.charLength)
        assertEquals(expected, loc.characters.toString())
    }

    // Deeply nested block at EOF: multiple queued dedents triggered by EOF
    @Test
    fun `deeply nested block at EOF location`() {
        val source = """
            def: a()
              if: b
                do:
                  c()
        """.trimIndent()
        val form = source.readWithLocation()

        val expected = "def: a()\n  if: b\n    do:\n      c()"
        val loc = requireNotNull(form.loc) { "Form should have location information" }
        assertEquals(0, loc.charIndex)
        assertEquals(expected.length, loc.charLength)
        assertEquals(expected, loc.characters.toString())
    }
}
