package brj

import brj.ReaderTest.FormType.BOOLEAN
import brj.ReaderTest.FormType.VECTOR
import brj.emitter.QSymbol.Companion.mkQSym
import brj.emitter.Symbol.Companion.mkSym
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

        val qSym = QSymbolForm(mkQSym(formNS, mkSym(":${this.name.toLowerCase().capitalize()}Form")), null)
    }

    private fun collForm(formType: FormType, vararg forms: Form): Form =
        ListForm(listOf(formType.qSym, VectorForm(forms.toList(), null)), null)

    private fun quotedForm(formType: FormType, form: Form): Form =
        ListForm(listOf(formType.qSym, form), null)

    @Test
    internal fun `test quoting`() {
        assertEquals(
            listOf(collForm(VECTOR,
                quotedForm(BOOLEAN, BooleanForm(true, null)),
                quotedForm(FormType.SYMBOL, QuotedSymbolForm(mkSym("foo"), null)))),

            readForms("'[true foo]"))
    }
}
