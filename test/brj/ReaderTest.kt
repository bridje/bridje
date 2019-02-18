package brj

import brj.QSymbol.Companion.mkQSym
import brj.ReaderTest.FormType.*
import brj.Symbol.Companion.mkSym
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
                quotedForm(SYMBOL, QuotedSymbolForm(mkSym("foo"))))),

            readForms("'[true foo]"))
    }
}
