package brj.reader

import brj.reader.ReaderTest.FormType.BOOLEAN
import brj.reader.ReaderTest.FormType.VECTOR
import brj.runtime.QSymbol
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.VARIANT
import brj.runtime.Symbol
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

        private val formNS = Symbol(ID, "brj.forms")

        val qSym = QSymbolForm(QSymbol(formNS, Symbol(VARIANT, "${this.name.toLowerCase().capitalize()}Form")))
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
                quotedForm(FormType.SYMBOL, QuotedSymbolForm(Symbol(ID, "foo"))))),

            readForms("'[true foo]"))
    }
}
