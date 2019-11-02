package brj

import brj.ReaderTest.FormType.BOOLEAN
import brj.ReaderTest.FormType.VECTOR
import brj.runtime.QSymbol.Companion.mkQSym
import brj.runtime.Symbol.Companion.mkSym
import brj.reader.*
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

        val qSym = QSymbolForm(mkQSym(formNS, mkSym(":${this.name.toLowerCase().capitalize()}Form")))
    }

    private fun collForm(formType: FormType, vararg forms: Form): Form =
        ListForm(listOf(formType.qSym, VectorForm(forms.toList())))

    private fun quotedForm(formType: FormType, form: Form): Form =
        ListForm(listOf(formType.qSym, form))

    @Test
    internal fun `test quoting`() {
        assertEquals(
            listOf(collForm(VECTOR,
                quotedForm(BOOLEAN, BooleanForm(true)),
                quotedForm(FormType.SYMBOL, QuotedSymbolForm(mkSym("foo"))))),

            readForms("'[true foo]"))
    }
}
