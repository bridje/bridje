package brj

import brj.QSymbol.Companion.mkQSym
import brj.ReaderTest.FormType.BOOLEAN
import brj.ReaderTest.FormType.VECTOR
import brj.Symbol.Companion.mkSym
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal fun readForms(s: String): List<Form> = Source.newBuilder("bridje", s, "testSource").build().let { source ->
    FormReader(source).readForms(source.reader)
}

internal class ReaderTest {

    private enum class FormType {
        BOOLEAN,
        SYMBOL,
        VECTOR, LIST, SET, RECORD;

        private val formNS = mkSym("brj.forms")

        val qSym = QSymbolForm(null, mkQSym(formNS, mkSym(":${this.name.toLowerCase().capitalize()}Form")))
    }

    private fun collForm(formType: FormType, vararg forms: Form): Form =
        ListForm(null, listOf(formType.qSym, VectorForm(null, forms.toList())))

    private fun quotedForm(formType: FormType, form: Form): Form =
        ListForm(null, listOf(formType.qSym, form))

    @Test
    internal fun `test quoting`() {
        assertEquals(
            listOf(collForm(VECTOR,
                quotedForm(BOOLEAN, BooleanForm(null, true)),
                quotedForm(FormType.SYMBOL, QuotedSymbolForm(null, mkSym("foo"))))),

            readForms("'[true foo]"))
    }
}
