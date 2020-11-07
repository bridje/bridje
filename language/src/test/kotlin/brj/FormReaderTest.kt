package brj

import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FormReaderTest {

    private fun readForms(s: String): List<Form> {
        val source = Source.newBuilder("brj", s, "<ReaderTest>").build()
        return FormReader(source).use {
            it.readForms().toList()
        }
    }

    @Test
    internal fun `test IntForm`() {
        assertEquals(
            listOf(IntForm(42)),
            readForms("42"))
    }

    @Test
    internal fun `test ListForm`() {
        assertEquals(
            listOf(ListForm(listOf(IntForm(42), IntForm(12)))),
            readForms("(42 12)"))
    }

    @Test
    internal fun `test StringForm`() {
        fun assertYieldsString(expected: String, actual: String) {
            assertEquals(
                listOf(StringForm(expected)),
                readForms(actual))
        }

        assertYieldsString("foo", "\"foo\"")
        assertYieldsString("foo\n", "\"foo\\n\"")
        assertYieldsString("foo\"", "\"foo\\\"\"")
    }

    @Test
    internal fun `test line comment`() {
        assertEquals(
            listOf(ListForm(listOf(IntForm(12), IntForm(13)))),
            readForms("(12 ; comment \n 13)"))
    }

    @Test
    internal fun `test nested collection`() {
        assertEquals(
            listOf(SetForm(listOf(VectorForm(listOf(StringForm("foo")))))),
            readForms("#{[\"foo\"]}"))
    }
}