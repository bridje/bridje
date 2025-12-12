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
    this is KeywordForm && other is KeywordForm -> name == other.name
    this is ListForm && other is ListForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    this is VectorForm && other is VectorForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    this is SetForm && other is SetForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    this is MapForm && other is MapForm ->
        els.size == other.els.size && els.zip(other.els).all { (a, b) -> a.contentEquals(b) }
    else -> false
}

private fun sym(name: String) = SymbolForm(name)
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
}
