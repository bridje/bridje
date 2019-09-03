package brj

import brj.QSymbol.Companion.mkQSym
import brj.ReaderTest.FormType.BOOLEAN
import brj.ReaderTest.FormType.VECTOR
import brj.Symbol.Companion.mkSym
import org.junit.jupiter.api.Test
import java.io.StringReader
import kotlin.test.assertEquals

internal class ReaderTest {

    private enum class FormType {
        BOOLEAN,
        SYMBOL,
        VECTOR, LIST, SET, RECORD;

        private val formNS = mkSym("brj.forms")

        val qSym = QSymbolForm(null, mkQSym(formNS, mkSym(":${this.name.toLowerCase().capitalize()}Form")))

    }

    object MockLocFactory : LocFactory<Nothing?> {
        override fun makeLoc(): Nothing? = null
        override fun makeLoc(startIndex: Int, stopIndex: Int): Nothing? = null
    }

    fun readForms(s: String): List<Form> = FormReader(MockLocFactory).readForms(StringReader(s))

    private fun collForm(formType: FormType, vararg forms: Form<Nothing?>): Form<Nothing?> =
        ListForm(null, listOf(formType.qSym, VectorForm(null, forms.toList())))

    private fun quotedForm(formType: FormType, form: Form<Nothing?>): Form<Nothing?> =
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
