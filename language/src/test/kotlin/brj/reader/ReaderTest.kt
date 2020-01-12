package brj.reader

import brj.reader.ReaderTest.FormType.*
import brj.runtime.QSymbol
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.VARIANT
import brj.runtime.Symbol
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.TypeLiteral
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val readFormsSource = Source.newBuilder("brj", "read-forms", "<read-forms>").internal(true).build()

internal fun readForms(s: String, ctx: Context = Context.getCurrent()): List<Form> = ctx.eval(readFormsSource).execute(s).`as`(object: TypeLiteral<List<Form>>() {})

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
                quotedForm(SYMBOL, QuotedSymbolForm(Symbol(ID, "foo"))))),

            readForms("'[true foo]"))
    }
}
